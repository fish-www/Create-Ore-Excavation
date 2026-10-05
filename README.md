# Create-Ore-Excavation
Extract resources using machines powered by Rotational Force

Download: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-ore-excavation), [Modrinth](https://modrinth.com/mod/create-ore-excavation)

## CC: Tweaked
The ore vein finder can be attached to a turtle and has one method:  
`local useSuccess, veinFound, veinId, veinSize = finder.search()`

## Where the ore veins are
The mod ships the ore vein **mechanism**, not the ore veins: on its own it generates no ore at
all, and it has no vein, cluster, drilling or extracting recipe. Write them as a data pack
(`<world>/datapacks/` or, with KubeJS, `<instance>/kubejs/data/`) — the format is documented in
[`docs/data-and-recipes.md`](docs/data-and-recipes.md) — or as KubeJS scripts, 1.21:
```js
//Output with chance has changed to coeutil.processingOutput(<item>, <chance 0-1>) from 1.20

ServerEvents.recipes(event => {
	
	//Adding veins
	//.placement(spacing, separation, salt)
	//If all three values match the veins overwrite each other
	//Use .priority(<value>) to set the vein generation priority
	event.recipes.createoreexcavation.vein('{"text": "My redstone vein"}', 'minecraft:redstone')
		.placement(1024, 128, 64825185)
		.id("kubejs:my_redstone_vein")
	
	//Drilling recipes (Items)
	//Arguments: output item(s), ore vein id, extraction time in ticks at 32 RPM.
	event.recipes.createoreexcavation.drilling('minecraft:redstone', 'kubejs:my_redstone_vein', 100)
		.id("kubejs:my_vein1");
	
	//Coal vein with 5% chance for diamond and require a diamond drill and lava for drilling
	//Always finite, 256 chunks grid spacing and a 20k-200k (mean 150k) reserve
	//Use .priority(<value>) for duplicate recipes with different inputs, higher values take priority
	event.recipes.createoreexcavation.vein('{"text": "My coal vein"}', 'minecraft:coal')
		.placement(2048, 128, 64457512).alwaysFinite().density(256).reserve(20000, 200000, 150000).id("kubejs:my_coal_vein")
		
	event.recipes.createoreexcavation.drilling('minecraft:coal', 'kubejs:my_coal_vein', 1000)
		.id("kubejs:my_coal1");
	event.recipes.createoreexcavation.drilling(
		[
			Item.of('minecraft:coal_block'),
			coeutil.processingOutput('minecraft:diamond', 0.05)
		], 'kubejs:my_coal_vein', 500)
		.drill('createoreexcavation:diamond_drill').fluid('minecraft:lava').priority(1)
		.id("kubejs:my_coal2");
	
	//Iron vein only in overworld and a stress requirement of 512 xRPM (default is 256 xRPM)
	//With a 20k-200k (mean 100k) reserve (if finite veins are enabled)
	event.recipes.createoreexcavation.vein('{"text": "My iron vein"}', 'minecraft:iron_ore')
		.placement(1024, 128, 6894685).density(128).reserve(20000, 200000, 100000).biomeWhitelist('forge:is_overworld')
		.id("kubejs:my_iron_vein")
	event.recipes.createoreexcavation.drilling('minecraft:raw_iron', 'kubejs:my_iron_vein', 100)
		.stress(512).id("kubejs:my_vein3");
	//biomeBlacklist is also available

	//Ore vein with an extra distribution in the mountains and one pinned chunk
	//biomeOverride(target, density, min, max, mean, sigma), target is one biome tag (#minecraft:is_mountain)
	//or one biome id (minecraft:stony_peaks). Use .biomeOverrideDensity(target, density) to keep the reserve.
	//.chunk(x, z) pins the vein to that chunk, .density(-1) keeps it off the grids
	event.recipes.createoreexcavation.vein('{"text": "My pinned vein"}', 'minecraft:raw_iron')
		.placement(64, 8, 424242).density(-1)
		.biomeOverride('#minecraft:is_mountain', 128, 20000, 200000, 150000, 25000)
		.chunk(100, 200).chunk(101, 200)
		.id("kubejs:my_pinned_vein");

	//Ore cluster (only the handheld drill can mine or detect these), waypointColor sets the map marker
	//colour (black, gold, white, aqua, ...), rare veins and clusters without one fall back
	event.recipes.createoreexcavation.vein('{"translate":"vein.coe.cluster_name","with":[{"text":"My ore"}]}', 'minecraft:raw_gold')
		.placement(64, 8, 123456).cluster().rare().waypointColor('light_blue')
		.id("kubejs:my_cluster");

	//Fluid extractor recipes (Fluids)
	//Lava as drilling fluid
	event.recipes.createoreexcavation.vein('{"text": "Water well"}', 'minecraft:water_bucket')
		.placement(1024, 128, 64630185).alwaysInfinite().id("kubejs:my_water_well")
	event.recipes.createoreexcavation.extracting('2Bx minecraft:water', 'kubejs:my_water_well', 10)
		.fluid('10x minecraft:lava').id("kubejs:test");
	//The drilling fluid, stress and drill settings are the same as the drilling recipe
	
	//Set base value in config for finite veins
});

//Add any new drill items to #createoreexcavation:drills item tag
//Place a drill texture under assets/<item mod id>/textures/entity/drill/<item name>.png
//See assets/createoreexcavation/textures/entity/drill/drill.png

```

## 1.20.1

https://github.com/tom5454/Create-Ore-Excavation/tree/1.20?tab=readme-ov-file#kubejs

## 1.19.2 or older
[https://github.com/tom5454/Create-Ore-Excavation/blob/1.19/README.md](https://github.com/tom5454/Create-Ore-Excavation/blob/1.19/README.md#kubejs)