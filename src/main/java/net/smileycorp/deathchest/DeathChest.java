package net.smileycorp.deathchest;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;


@EventBusSubscriber
@Mod(modid = "deathchest", acceptableRemoteVersions="*", version = "1.5")
public class DeathChest {
	
	public static boolean hasSkull;
	public static boolean lockChest;
	public static boolean giveJournal;
	public static boolean journalPos;
	
	@EventHandler
	public void preInit(FMLPreInitializationEvent event){
		Config.config = new Configuration(event.getSuggestedConfigurationFile());
		Config.syncConfig();
	}
	
	@SubscribeEvent
	public static void onDeathEvent(LivingDeathEvent event) {
		World world = event.getEntity().world;
		if (event.getEntityLiving() instanceof EntityPlayer &! world.isRemote) {
			EntityPlayer player = (EntityPlayer)event.getEntityLiving();
			NonNullList<ItemStack> items = NonNullList.create();
			NonNullList<ItemStack> items2 = NonNullList.create();
			InventoryPlayer inventory = player.inventory;
			for (ItemStack stack : inventory.mainInventory) {
				addStack(stack, items, items2, 27);
			}
			for (ItemStack stack : inventory.armorInventory) {
				addStack(stack, items, items2, 27);
			}
			addStack(inventory.offHandInventory.get(0), items, items2, 27);
			IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
			if (baubles != null) {
				for (int i = 0; i < baubles.getSlots(); i++) {
					addStack(baubles.getStackInSlot(i), items, items2, 27);
					baubles.setStackInSlot(i, ItemStack.EMPTY); // ensure baubles are actually removed.
				}
			}
			if (items.size()>0) {
				for (double i = player.getPosition().getY(); i < 255; i++) {
					BlockPos pos = new BlockPos(player.getPosition().getX(), i, player.getPosition().getZ());
					Block block = world.getBlockState(pos).getBlock();
					if (world.isAirBlock(pos)|| block instanceof BlockBush || block instanceof BlockFluidBase){
						world.setBlockState(pos, Blocks.CHEST.getDefaultState());

						
						NBTTagCompound nbt;
						
						TileEntityChest te = new TileEntityChest();
						
						te.setCustomName(player.getDisplayName().getUnformattedText()+"'s Loot");
						for (int slot = 0; slot<items.size(); slot++) {
							te.setInventorySlotContents(slot, items.get(slot));
						}
						if(lockChest) {
							nbt=te.writeToNBT(new NBTTagCompound());
							nbt.setString("Lock", player.getUniqueID().toString());
							te.readFromNBT(nbt);
							te.markDirty();
						}
						world.setTileEntity(pos, te);
						
						if (items2.size()>0) {
							pos = pos.up();
							block = world.getBlockState(pos).getBlock();
							world.setBlockState(pos, Blocks.CHEST.getDefaultState());
							te = new TileEntityChest();
							te.setCustomName(player.getDisplayName().getUnformattedText()+"'s Loot");
							for (int slot = 0; slot<items2.size(); slot++) {
								te.setInventorySlotContents(slot, items2.get(slot));
							}
							if(lockChest) {
								nbt=te.writeToNBT(new NBTTagCompound());
								nbt.setString("Lock", player.getUniqueID().toString());
								te.readFromNBT(nbt);
								te.markDirty();
							}
							world.setTileEntity(pos, te);
						}
						
						if (hasSkull) {
							if(world.isAirBlock(pos.up())) {
								world.setBlockState(pos.up(), Blocks.SKULL.getDefaultState());
								TileEntitySkull skull = new TileEntitySkull();
								skull.setPlayerProfile(player.getGameProfile());
								world.setTileEntity(pos.up(), skull);
							}
						}
						event.setCanceled(true);
						break;
					}
				}
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onRespawnEvent(PlayerEvent.Clone event) {
		EntityPlayer player = event.getOriginal();
		if (!player.world.isRemote) {
			if (player!=null&&event.isWasDeath()&&giveJournal) {
				BlockPos pos = player.getPosition();
				long time = player.getEntityWorld().getWorldTime();
				NBTTagCompound nbt = new NBTTagCompound();
				nbt.setInteger("generation", 3);
				nbt.setString("title", "Death Journal");
				nbt.setString("author", player.getDisplayName().getFormattedText());
				String contents = "{\"text\":\"Death Time: "+time+"\\n\\nDimension: "+player.world.provider.getDimensionType().getName()+"\\n";
				if (journalPos) {
					contents += "\\nPosition: "+pos.getX()+", "+pos.getY()+", "+pos.getZ();
				}
				contents+="\"}";
				NBTTagList list = new NBTTagList();
				list.appendTag(new NBTTagString(contents));
				nbt.setTag("pages", list);
				ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
				if(lockChest) {
					nbt.setString("Key", player.getUniqueID().toString());
				}
				stack.setTagCompound(nbt);
				if(!event.getEntityPlayer().inventory.addItemStackToInventory(stack)) {
					if(!event.getEntityPlayer().getEntityWorld().isRemote) {
						event.getEntityPlayer().entityDropItem(stack, 1F);
					}
				}
				event.getEntityPlayer().inventoryContainer.detectAndSendChanges();
				event.getEntityPlayer().inventory.markDirty();
			}
		}
	}
	
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void interactBlockEvent(RightClickBlock event) {
		EntityPlayer player = event.getEntityPlayer();
		World world = player.world;
		if (!world.isRemote) {
			BlockPos pos = event.getPos();
			if (world.getTileEntity(pos) instanceof TileEntityChest) {
				TileEntityChest te = (TileEntityChest) world.getTileEntity(pos);
				NBTTagCompound nbt = te.writeToNBT(new NBTTagCompound());
				String lock = nbt.getString("Lock");
				if (lock != null) {
					ItemStack stack = player.getHeldItem(event.getHand());
					if (stack.getItem() == Items.WRITTEN_BOOK) {
						NBTTagCompound stackNBT = stack.getTagCompound();
						int gen = stackNBT.getInteger("generation");
						if (gen == 3) {
							String key = stackNBT.getString("Key");
							if (key != null && key.equals(lock)) {
								nbt.removeTag("Lock");
								te.readFromNBT(nbt);
								te.markDirty();
								player.sendStatusMessage(new TextComponentString("Chest has been unlocked."), false);
							}
						}
					}
				}
			}
		}
	}

	private static void addStack(ItemStack stack, NonNullList<ItemStack> items,
			NonNullList<ItemStack> items2, int size) {
		if (!stack.isEmpty()) {
			if (items.size()==size){
				items2.add(stack);
			}else {
				items.add(stack);
			}
		}
	}
	
	static class Config {
		
		public static Configuration config;
	
		public static void syncConfig(){
			try{
				config.load();
				//general
				hasSkull = config.get(Configuration.CATEGORY_GENERAL, "hasSkull",  false, "whether a death chest spawns with a skull above it").getBoolean();
				giveJournal = config.get(Configuration.CATEGORY_GENERAL, "giveJournal", true, "whether players should be given a journal of their death").getBoolean();
				lockChest = config.get(Configuration.CATEGORY_GENERAL, "lockChest", true, "whether chests should be locked so that only its owner's journal can open it\nonly works if giveJournal is true").getBoolean();
				journalPos = config.get(Configuration.CATEGORY_GENERAL, "journalPos", true, "whether journal shows death position\nonly works if giveJournal is true").getBoolean();
				
				
			} catch (Exception e) {
			} finally {
				if (config.hasChanged()) config.save();
			}
		}
	
	}
}
