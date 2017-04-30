package minecrafttransportsimulator;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import minecrafttransportsimulator.dataclasses.MTSRegistry;
import minecrafttransportsimulator.entities.core.EntityMultipartChild;
import minecrafttransportsimulator.systems.ConfigSystem;
import minecrafttransportsimulator.systems.ForgeContainerGUISystem;
import minecrafttransportsimulator.systems.PackParserSystem;
import minecrafttransportsimulator.systems.SFXSystem.SFXEntity;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**Contains registration methods used by {@link MTSRegistry} and methods overridden by ClientProxy. 
 * See the latter for more info on overridden methods.
 * 
 * @author don_bruce
 */
public class CommonProxy{
	private static int entityNumber = 0;
	private static int packetNumber = 0;

	public void preInit(FMLPreInitializationEvent event){
		ConfigSystem.initCommon(event.getSuggestedConfigurationFile());
		PackParserSystem.init();
	}
	
	public void init(FMLInitializationEvent event){
		MTSRegistry.instance.init();
		NetworkRegistry.INSTANCE.registerGuiHandler(MTS.instance, new ForgeContainerGUISystem());
	}
	
	/**
	 * Registers the given item and adds it to the creative tab list.
	 * @param item
	 */
	public void registerItem(Item item){
		item.setTextureName(MTS.MODID + ":" + item.getUnlocalizedName().substring(5).toLowerCase());
		GameRegistry.registerItem(item, item.getUnlocalizedName().substring(5));
		MTSRegistry.itemList.add(item);
	}
	
	/**
	 * Registers the given block and adds it to the creative tab list.
	 * Also adds the respective TileEntity if the block has one.
	 * @param block
	 */
	public void registerBlock(Block block){
		block.setBlockTextureName(MTS.MODID + ":" + block.getUnlocalizedName().substring(5).toLowerCase());
		GameRegistry.registerBlock(block, block.getUnlocalizedName().substring(5));
		MTSRegistry.itemList.add(Item.getItemFromBlock(block));
		if(block instanceof ITileEntityProvider){
			Class<? extends TileEntity> tileEntityClass = ((ITileEntityProvider) block).createNewTileEntity(null, 0).getClass();
			GameRegistry.registerTileEntity(tileEntityClass, tileEntityClass.getSimpleName());
		}
	}

	/**
	 * Registers an entity.
	 * Optionally pairs the entity with an item for GUI operations.
	 * @param entityClass
	 * @param entityItem
	 */
	public void registerEntity(Class entityClass){
		EntityRegistry.registerModEntity(entityClass, entityClass.getSimpleName().substring(6), entityNumber++, MTS.MODID, 80, 5, false);
	}
	
	/**
	 * Registers an entity.
	 * Optionally pairs the entity with an item for GUI operations.
	 * @param entityClass
	 * @param entityItem
	 */
	public void registerChildEntity(Class<? extends EntityMultipartChild> entityClass, Item entityItem){
		if(entityItem != null){
			MTSRegistry.entityItems.put(entityClass, entityItem);
		}
		registerEntity(entityClass);
	}
	
	/**
	 * Registers a packet and its handler on the client and/or the server.
	 * @param packetClass
	 * @param handlerClass
	 * @param client
	 * @param server
	 */
	public <REQ extends IMessage, REPLY extends IMessage> void registerPacket(Class<REQ> packetClass, Class<? extends IMessageHandler<REQ, REPLY>> handlerClass, boolean client, boolean server){
		if(client)MTS.MFSNet.registerMessage(handlerClass, packetClass, ++packetNumber, Side.CLIENT);
		if(server)MTS.MFSNet.registerMessage(handlerClass, packetClass, ++packetNumber, Side.SERVER);
	}
	
	public void registerRecpie(ItemStack output, Object...params){
		GameRegistry.addRecipe(output, params);
	}
	
	public void openGUI(Object clicked, EntityPlayer clicker){}
	public void playSound(Entity noisyEntity, String soundName, float volume, float pitch){}
	public void updateSFXEntity(SFXEntity entity, World world){}
}
