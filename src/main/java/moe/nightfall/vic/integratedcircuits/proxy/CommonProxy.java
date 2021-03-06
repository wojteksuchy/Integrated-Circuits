package moe.nightfall.vic.integratedcircuits.proxy;

import static moe.nightfall.vic.integratedcircuits.IntegratedCircuits.logger;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Objects;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Maps;

import codechicken.lib.vec.BlockCoord;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import moe.nightfall.vic.integratedcircuits.Config;
import moe.nightfall.vic.integratedcircuits.Constants;
import moe.nightfall.vic.integratedcircuits.Content;
import moe.nightfall.vic.integratedcircuits.DiskDrive;
import moe.nightfall.vic.integratedcircuits.DiskDrive.IDiskDrive;
import moe.nightfall.vic.integratedcircuits.IntegratedCircuits;
import moe.nightfall.vic.integratedcircuits.LaserHelper.Laser;
import moe.nightfall.vic.integratedcircuits.client.gui.IntegratedCircuitsGuiHandler;
import moe.nightfall.vic.integratedcircuits.misc.MiscUtils;
import moe.nightfall.vic.integratedcircuits.misc.RayTracer;
import moe.nightfall.vic.integratedcircuits.net.AbstractPacket;
import moe.nightfall.vic.integratedcircuits.net.MCDataOutputImpl;
import moe.nightfall.vic.integratedcircuits.net.Packet7SegmentChangeMode;
import moe.nightfall.vic.integratedcircuits.net.Packet7SegmentOpenGui;
import moe.nightfall.vic.integratedcircuits.net.PacketAssemblerChangeItem;
import moe.nightfall.vic.integratedcircuits.net.PacketAssemblerChangeLaser;
import moe.nightfall.vic.integratedcircuits.net.PacketAssemblerStart;
import moe.nightfall.vic.integratedcircuits.net.PacketAssemblerUpdate;
import moe.nightfall.vic.integratedcircuits.net.PacketAssemblerUpdateInsufficient;
import moe.nightfall.vic.integratedcircuits.net.PacketChangeSetting;
import moe.nightfall.vic.integratedcircuits.net.PacketDataStream;
import moe.nightfall.vic.integratedcircuits.net.PacketFloppyDisk;
import moe.nightfall.vic.integratedcircuits.net.pcb.PacketPCBCache;
import moe.nightfall.vic.integratedcircuits.net.pcb.PacketPCBChangeInput;
import moe.nightfall.vic.integratedcircuits.net.pcb.PacketPCBChangeName;
import moe.nightfall.vic.integratedcircuits.net.pcb.PacketPCBChangePart;
import moe.nightfall.vic.integratedcircuits.net.pcb.PacketPCBClear;
import moe.nightfall.vic.integratedcircuits.net.pcb.PacketPCBComment;
import moe.nightfall.vic.integratedcircuits.net.pcb.PacketPCBDeleteComment;
import moe.nightfall.vic.integratedcircuits.net.pcb.PacketPCBLoad;
import moe.nightfall.vic.integratedcircuits.net.pcb.PacketPCBPrint;
import moe.nightfall.vic.integratedcircuits.net.pcb.PacketPCBSaveLoad;
import moe.nightfall.vic.integratedcircuits.net.pcb.PacketPCBSimulation;
import moe.nightfall.vic.integratedcircuits.net.pcb.PacketPCBUpdate;
import moe.nightfall.vic.integratedcircuits.tile.TileEntityAssembler;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;

public class CommonProxy {
	public static int serverTicks;
	public static SimpleNetworkWrapper networkWrapper;
	private static HashMap<World, HashMap<SidedBlockCoord, MCDataOutputImpl>> out = Maps.newHashMap();

	public void initialize() {
		NetworkRegistry.INSTANCE.registerGuiHandler(IntegratedCircuits.instance, new IntegratedCircuitsGuiHandler());
	}

	public void preInitialize() {
		MinecraftForge.EVENT_BUS.register(this);
		FMLCommonHandler.instance().bus().register(this);

		networkWrapper = NetworkRegistry.INSTANCE.newSimpleChannel(Constants.MOD_ID);

		logger.debug("[Common Proxy]: Registering network packets");
		AbstractPacket.registerPacket(PacketPCBUpdate.class, Side.CLIENT, 0);
		AbstractPacket.registerPacket(PacketPCBChangePart.class, Side.SERVER, 1);
		AbstractPacket.registerPacket(PacketPCBClear.class, null, 2);
		AbstractPacket.registerPacket(PacketPCBChangeName.class, null, 3);
		AbstractPacket.registerPacket(PacketPCBSaveLoad.class, Side.SERVER, 4);
		AbstractPacket.registerPacket(PacketPCBChangeInput.class, null, 5);
		AbstractPacket.registerPacket(PacketPCBLoad.class, Side.CLIENT, 6);
		AbstractPacket.registerPacket(PacketPCBCache.class, Side.SERVER, 7);
		AbstractPacket.registerPacket(PacketPCBComment.class, null, 18);
		AbstractPacket.registerPacket(PacketPCBDeleteComment.class, null, 19);
		AbstractPacket.registerPacket(PacketPCBSimulation.class, null, 20);
		AbstractPacket.registerPacket(PacketPCBPrint.class, Side.SERVER, 21);

		AbstractPacket.registerPacket(PacketAssemblerStart.class, null, 8);
		AbstractPacket.registerPacket(PacketAssemblerUpdate.class, Side.CLIENT, 9);
		AbstractPacket.registerPacket(PacketAssemblerChangeLaser.class, Side.CLIENT, 10);
		AbstractPacket.registerPacket(PacketAssemblerChangeItem.class, Side.CLIENT, 11);
		AbstractPacket.registerPacket(PacketAssemblerUpdateInsufficient.class, Side.CLIENT, 12);

		AbstractPacket.registerPacket(PacketChangeSetting.class, null, 13);
		AbstractPacket.registerPacket(PacketFloppyDisk.class, Side.CLIENT, 14);

		AbstractPacket.registerPacket(Packet7SegmentOpenGui.class, Side.CLIENT, 15);
		AbstractPacket.registerPacket(Packet7SegmentChangeMode.class, null, 16);

		AbstractPacket.registerPacket(PacketDataStream.class, Side.CLIENT, 17);
	}

	public synchronized MCDataOutputImpl addStream(World world, BlockCoord crd, int side) {
		if (world.isRemote)
			throw new IllegalArgumentException("Cannot use getWriteStream on a client world");
		SidedBlockCoord scrd = new SidedBlockCoord(crd.x, crd.y, crd.z, side);
		if (!out.containsKey(world))
			out.put(world, new HashMap<SidedBlockCoord, MCDataOutputImpl>());
		HashMap<SidedBlockCoord, MCDataOutputImpl> map = out.get(world);

		if (map.containsKey(scrd))
			CommonProxy.networkWrapper.sendToDimension(new PacketDataStream(map.remove(scrd), scrd.x, scrd.y, scrd.z,
					scrd.side), world.provider.dimensionId);

		MCDataOutputImpl stream = new MCDataOutputImpl(new ByteArrayOutputStream());
		map.put(scrd, stream);
		return stream;
	}

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase == Phase.END) {
			serverTicks++;

			for (World world : out.keySet()) {
				HashMap<SidedBlockCoord, MCDataOutputImpl> map = out.get(world);
				for (Entry<SidedBlockCoord, MCDataOutputImpl> entry : map.entrySet()) {
					SidedBlockCoord crd = entry.getKey();
					CommonProxy.networkWrapper.sendToDimension(new PacketDataStream(map.get(crd), crd.x, crd.y, crd.z,
							crd.side), world.provider.dimensionId);
				}
				map.clear();
			}
		}
	}

	private static class SidedBlockCoord {
		public int x, y, z, side;

		public SidedBlockCoord(int x, int y, int z, int side) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.side = side;
		}

		@Override
		public int hashCode() {
			return Objects.hash(x, y, z, side);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SidedBlockCoord other = (SidedBlockCoord) obj;
			return other.x == x && other.y == y && other.z == z && other.side == side;
		}
	}

	@SubscribeEvent
	public void onPlayerJoined(PlayerLoggedInEvent event) {
		if (Config.showStartupMessage) {
			ChatComponentText text = new ChatComponentText(
					"[Integrated Circuits] This is an extremely early alpha version so please report any bugs occuring to the ");
			ChatComponentText url = new ChatComponentText("GitHub");
			url.getChatStyle().setUnderlined(true);
			url.getChatStyle().setColor(EnumChatFormatting.BLUE);
			url.getChatStyle()
				.setChatHoverEvent(
						new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(
								"Click to visit ICs GitHub repo")));
			url.getChatStyle().setChatClickEvent(
					new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/Victorious3/Integrated-Circuits"));
			text.appendSibling(url);
			text.appendText(" repo.");
			if (event.player.canCommandSenderUseCommand(MinecraftServer.getServer().getOpPermissionLevel(), null))
				text.appendText(" You can disable this message by changing the config file. Thanks for your attention.");
			event.player.addChatComponentMessage(text);
		}
	}

	@SubscribeEvent
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.action != Action.RIGHT_CLICK_BLOCK)
			return;
		Block block = event.world.getBlock(event.x, event.y, event.z);
		if (!(block.hasTileEntity(event.world.getBlockMetadata(event.x, event.y, event.z))))
			return;
		TileEntity te = (TileEntity) event.world.getTileEntity(event.x, event.y, event.z);

		if (te instanceof IDiskDrive) {
			IDiskDrive drive = (IDiskDrive) te;

			ItemStack stack = event.entityPlayer.getCurrentEquippedItem();

			MovingObjectPosition target = RayTracer.rayTrace(event.entityPlayer, 1F);
			if (target == null)
				return;
			AxisAlignedBB box = DiskDrive.getDiskDriveBoundingBox(drive, event.x, event.y, event.z, target.hitVec);
			if (box != null) {
				if (!event.world.isRemote) {
					if (stack == null) {
						ItemStack floppy = drive.getDisk();
						drive.setDisk(null);
						event.entityPlayer.setCurrentItemOrArmor(0, floppy);
					} else if (stack.getItem() != null && stack.getItem() == Content.itemFloppyDisk
							&& drive.getDisk() == null) {
						drive.setDisk(stack);
						event.entityPlayer.setCurrentItemOrArmor(0, null);
					}
				}
				event.useBlock = Result.DENY;
				event.useItem = Result.DENY;
			}
		}
		if (te instanceof TileEntityAssembler) {
			TileEntityAssembler assembler = (TileEntityAssembler) te;
			Pair<AxisAlignedBB, Integer> result = getLaserBoundingBox(assembler, event.x, event.y, event.z,
					event.entityPlayer, 1);
			if (result.getLeft() != null) {
				if (!event.world.isRemote) {
					ItemStack holding = event.entityPlayer.getHeldItem();
					ItemStack stack2 = holding;
					if (holding != null) {
						stack2 = holding.copy();
						stack2.stackSize = 1;
					}
					assembler.laserHelper.createLaser(result.getRight(), stack2);
					if (holding == null)
						event.entityPlayer.inventory.setInventorySlotContents(event.entityPlayer.inventory.currentItem,
								new ItemStack(Content.itemLaser));
					else if (holding.getItem() == Content.itemLaser) {
						holding.stackSize--;
						if (holding.stackSize <= 0)
							holding = null;
					}
				}
				event.useBlock = Result.DENY;
				event.useItem = Result.DENY;
			}
		}
	}

	public Pair<AxisAlignedBB, Integer> getLaserBoundingBox(TileEntityAssembler te, int x, int y, int z,
			EntityPlayer player, float partialTicks) {
		if (te.getStatus() == te.RUNNING || !player.isSneaking())
			return new ImmutablePair(null, null);
		boolean holdsEmpty = player.getHeldItem() == null;
		boolean holdsLaser = !holdsEmpty ? player.getHeldItem().getItem() == Content.itemLaser : false;

		AxisAlignedBB base = AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 8 / 16F, 1).offset(x, y, z);
		AxisAlignedBB boxBase = AxisAlignedBB.getBoundingBox(11 / 16F, 8 / 16F, 11 / 16F, 15 / 16F, 15 / 16F, 15 / 16F);
		AxisAlignedBB box1 = null, box2 = null, box3 = null, box4 = null;

		Laser l1 = te.laserHelper.getLaser((te.rotation + 0) % 4);
		if (l1 != null && holdsEmpty || holdsLaser && l1 == null)
			box1 = MiscUtils.getRotatedInstance(boxBase, 2).offset(x, y, z);
		Laser l2 = te.laserHelper.getLaser((te.rotation + 1) % 4);
		if (l2 != null && holdsEmpty || holdsLaser && l2 == null)
			box2 = MiscUtils.getRotatedInstance(boxBase, 1).offset(x, y, z);
		Laser l3 = te.laserHelper.getLaser((te.rotation + 2) % 4);
		if (l3 != null && holdsEmpty || holdsLaser && l3 == null)
			box3 = MiscUtils.getRotatedInstance(boxBase, 0).offset(x, y, z);
		Laser l4 = te.laserHelper.getLaser((te.rotation + 3) % 4);
		if (l4 != null && holdsEmpty || holdsLaser && l4 == null)
			box4 = MiscUtils.getRotatedInstance(boxBase, 3).offset(x, y, z);

		MovingObjectPosition mop = RayTracer.rayTraceAABB(player, partialTicks, base, box1, box2, box3, box4);
		if (mop == null || mop.hitInfo == base)
			return new ImmutablePair(null, null);

		int id = (te.rotation + (mop.hitInfo == box1 ? 0 : mop.hitInfo == box2 ? 1 : mop.hitInfo == box3 ? 2
				: mop.hitInfo == box4 ? 3 : 0)) % 4;
		return new ImmutablePair((AxisAlignedBB) mop.hitInfo, id);
	}
}
