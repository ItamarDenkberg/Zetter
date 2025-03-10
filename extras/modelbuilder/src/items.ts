import fs from "fs";
import {materialVariations, paintingVariations, plateVariations} from "./variations";
import rimraf from "rimraf";

export interface MinecraftModelItemOverride {
    predicate: { [key: string]: number },
    model: string
}

export interface MinecraftModelItem {
    parent: string,
    textures: {
        [key: string]: string
    },
    overrides?: MinecraftModelItemOverride[],
    __comment?: string
}

export const buildItems = function () {
    console.log(`==========================`);
    console.log(`Processing icon variations`);

    rimraf.sync(`result/models/`);

    fs.mkdirSync(`result/models/`);
    fs.mkdirSync(`result/models/item/`);
    fs.mkdirSync(`result/models/item/frame/`);

    // Real icons
    for (let material of materialVariations) {
        console.log(`Processing ${material} real icons`);

        for (let plateVariation of Object.values(plateVariations)) {
            for (let paintingVariation of Object.values(paintingVariations)) {
                let itemModel: MinecraftModelItem = {
                    parent: "item/generated",
                    textures: {
                        layer0: `zetter:item/frame/${material}_${plateVariation}`,
                    },
                }

                if (paintingVariation == paintingVariations.PAINTING) {
                    itemModel.textures.layer1 = "zetter:item/frame/painting";
                }

                fs.writeFileSync(`result/models/item/frame/${material}_${plateVariation}_${paintingVariation}.json`, JSON.stringify(itemModel));
            }
        }
    }

    // Overrides
    for (let material of materialVariations) {
        console.log(`Processing ${material} reference icons`);

        for (let plateVariation of Object.values(plateVariations)) {
            let itemModel: MinecraftModelItem = {
                parent: "item/generated",
                textures: {
                    layer0: `zetter:item/frame/${material}_${plateVariation}`,
                },
                overrides: [
                    { predicate: { painting: 0 }, model: `zetter:item/frame/${material}_${plateVariation}_${paintingVariations.EMPTY}` },
                    { predicate: { painting: 1 }, model: `zetter:item/frame/${material}_${plateVariation}_${paintingVariations.PAINTING}` }
                ]
            }

            fs.writeFileSync(`result/models/item/${material}_${plateVariation}_frame.json`, JSON.stringify(itemModel));
        }
    }
};