#!/usr/bin/env python3
"""The local game store, as a village building.

One layout, five villages. What changes between plains and desert is which blocks the walls
are made of and whether the roof slopes; what does not change is the shop - a counter with a
till gap in it, a chest of stock behind that, and two tables to play at. So there is one
description of a card shop here rather than five buildings to keep in step.

The jigsaw wiring is copied from vanilla's own small houses, checked block for block against
`plains_small_house_1`: an entrance jigsaw in the middle of the west face pointing at the
street pool, and a villager jigsaw in the middle of the floor, which is what actually puts
somebody in the shop.

These are placeholders in the same sense the textures are: real, correct, and somebody else's
to make beautiful. Replace the .nbt files and nothing else has to change.

    python3 tools/village.py
"""

import os
import sys
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtio  # noqa: E402

OUT = "common/src/main/resources/data/gathering/structure/village"

# 1.21.1. A structure with the wrong one is run through the data fixers, which is a silent
# way to end up with a building nobody meant.
DATA_VERSION = 3955

# Eleven by nine, which is a room seven by five once the walls and the eaves are counted.
#
# The depth is what fixes it. A table seats two people facing each other across its north and
# south edges - never its sides, because a board read sideways is not a board - so a table
# needs a clear block in front of it and a clear block behind it. Five deep is a row of seats,
# a row of table, and a row of seats, and there is no smaller number that works. Seven wide is
# two tables with room to walk between them, and the counter.
WIDE, DEEP = 11, 9
LOW_X, HIGH_X = 1, WIDE - 2      # the walls
LOW_Z, HIGH_Z = 1, DEEP - 2
INNER_X = (2, WIDE - 3)          # the room
INNER_Z = (2, DEEP - 3)

# The shop, in the room. The counter runs down the east side with a gap to get behind it, the
# stock is behind that, and the two tables face the door.
COUNTER_X = 7
BEHIND_X = 8
COUNTER_GAP_Z = 4
TABLES_X = ((2, 3), (5, 6))
TABLE_Z = (3, 4)
DOOR_Z = 6

CHEST_LOOT = "gathering:chests/card_shop"


def air():
    return "minecraft:air"


class Style:
    """One village's materials, and how its roof is built."""

    def __init__(self, name, wall, post, floor, footing, door, roof=None,
                 roof_fill=None, flat=None, parapet=None, glass="minecraft:glass_pane",
                 step=None):
        self.name = name
        self.wall = wall
        self.post = post
        self.floor = floor
        self.footing = footing
        self.door = door
        self.roof = roof
        self.roof_fill = roof_fill or wall
        self.flat = flat
        self.parapet = parapet
        self.glass = glass
        self.step = step or roof

    def post_props(self):
        """A log stands up; a carved block has nothing to say about which way it faces."""
        return {"axis": "y"} if ("log" in self.post or "wood" in self.post) else None


STYLES = [
    Style("plains",
          wall="minecraft:oak_planks", post="minecraft:stripped_oak_log",
          floor="minecraft:oak_planks", footing="minecraft:cobblestone",
          door="minecraft:oak_door", roof="minecraft:oak_stairs"),
    Style("savanna",
          wall="minecraft:acacia_planks", post="minecraft:acacia_wood",
          floor="minecraft:acacia_planks", footing="minecraft:yellow_terracotta",
          door="minecraft:acacia_door", roof="minecraft:acacia_stairs"),
    Style("taiga",
          wall="minecraft:spruce_planks", post="minecraft:spruce_log",
          floor="minecraft:spruce_planks", footing="minecraft:cobblestone",
          door="minecraft:spruce_door", roof="minecraft:spruce_stairs"),
    Style("snowy",
          wall="minecraft:spruce_planks", post="minecraft:stripped_spruce_log",
          floor="minecraft:spruce_planks", footing="minecraft:cobblestone",
          door="minecraft:spruce_door", roof="minecraft:spruce_stairs",
          roof_fill="minecraft:snow_block"),
    # Desert roofs are flat, which is the one real difference in the whole set.
    Style("desert",
          wall="minecraft:cut_sandstone", post="minecraft:chiseled_sandstone",
          floor="minecraft:smooth_sandstone", footing="minecraft:sandstone",
          door="minecraft:jungle_door", flat="minecraft:smooth_sandstone",
          parapet="minecraft:sandstone_wall", step="minecraft:smooth_sandstone_stairs"),
]


class Build:
    """A grid of block states, turned into a palette and a block list at the end."""

    def __init__(self):
        self.cells = {}
        self.data = {}

    def put(self, x, y, z, name, props=None, nbt=None):
        self.cells[(x, y, z)] = (name, tuple(sorted((props or {}).items())))
        if nbt:
            self.data[(x, y, z)] = nbt

    def fill(self, x0, x1, y0, y1, z0, z1, name, props=None):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.put(x, y, z, name, props)

    def tag(self, size):
        palette = []
        index = {}
        blocks = []
        sx, sy, sz = size
        for y in range(sy):
            for z in range(sz):
                for x in range(sx):
                    name, props = self.cells.get((x, y, z), (air(), ()))
                    key = (name, props)
                    if key not in index:
                        index[key] = len(palette)
                        palette.append(key)
                    entry = OrderedDict()
                    entry["pos"] = nbtio.Tag(nbtio.LIST, (nbtio.INT, [x, y, z]))
                    entry["state"] = nbtio.Tag(nbtio.INT, index[key])
                    extra = self.data.get((x, y, z))
                    if extra:
                        held = OrderedDict()
                        for field, value in extra.items():
                            held[field] = nbtio.Tag(nbtio.STRING, value)
                        entry["nbt"] = nbtio.Tag(nbtio.COMPOUND, held)
                    blocks.append(entry)

        listed = []
        for name, props in palette:
            entry = OrderedDict()
            if props:
                held = OrderedDict()
                for field, value in props:
                    held[field] = nbtio.Tag(nbtio.STRING, value)
                entry["Properties"] = nbtio.Tag(nbtio.COMPOUND, held)
            entry["Name"] = nbtio.Tag(nbtio.STRING, name)
            listed.append(entry)

        root = OrderedDict()
        root["size"] = nbtio.Tag(nbtio.LIST, (nbtio.INT, list(size)))
        root["entities"] = nbtio.Tag(nbtio.LIST, (nbtio.END, []))
        root["blocks"] = nbtio.Tag(nbtio.LIST, (nbtio.COMPOUND, blocks))
        root["palette"] = nbtio.Tag(nbtio.LIST, (nbtio.COMPOUND, listed))
        root["DataVersion"] = nbtio.Tag(nbtio.INT, DATA_VERSION)
        return nbtio.Tag(nbtio.COMPOUND, root)


def stair_facing(x, z, west, east, north, south):
    """Which way a roof stair on this ring points, taken from vanilla's own.

    Every stair faces in towards the ridge, and the four corners are outer pieces. The corner
    shapes are the ones `plains_small_house_1` uses, rather than reasoned out, because a roof
    with one corner turned the wrong way is a roof somebody has to notice.
    """
    if x == west and z == north:
        return "south", "outer_left"
    if x == east and z == north:
        return "west", "outer_left"
    if x == west and z == south:
        return "east", "outer_left"
    if x == east and z == south:
        return "north", "outer_left"
    if z == north:
        return "south", "straight"
    if z == south:
        return "north", "straight"
    if x == west:
        return "east", "straight"
    return "west", "straight"


def inset(x, z):
    """How many rings in from the outside this spot is."""
    return min(x, WIDE - 1 - x, z, DEEP - 1 - z)


def shell(build, style, height):
    """Floor, walls, door, windows and the lights on either side of it."""
    west, east = INNER_X
    north, south = INNER_Z
    middle_x = WIDE // 2

    build.fill(LOW_X, HIGH_X, 0, 0, LOW_Z, HIGH_Z, style.footing)
    build.fill(west, east, 0, 0, north, south, style.floor)

    for y in range(1, height):
        for x in range(LOW_X, HIGH_X + 1):
            for z in range(LOW_Z, HIGH_Z + 1):
                edge = x in (LOW_X, HIGH_X) or z in (LOW_Z, HIGH_Z)
                corner = x in (LOW_X, HIGH_X) and z in (LOW_Z, HIGH_Z)
                if corner:
                    build.put(x, y, z, style.post, style.post_props())
                elif edge:
                    build.put(x, y, z, style.wall)

    # The way in, at the south end of the west wall. Off centre because the middle of that
    # wall is where somebody sitting at a table would be, and a door opening into a chair is
    # a door nobody can use.
    for half, y in (("lower", 1), ("upper", 2)):
        build.put(LOW_X, y, DOOR_Z, style.door, {
            "facing": "east", "half": half, "hinge": "right",
            "open": "false", "powered": "false"})

    # Windows, in the two walls a customer can see and the one behind the counter.
    for z in (north, north + 2):
        build.put(LOW_X, 2, z, style.glass, {
            "north": "true", "south": "true", "east": "false", "west": "false",
            "waterlogged": "false"})
    for z in (north + 1, south):
        build.put(HIGH_X, 2, z, style.glass, {
            "north": "true", "south": "true", "east": "false", "west": "false",
            "waterlogged": "false"})
    for x in (west + 1, east - 1):
        for z in (LOW_Z, HIGH_Z):
            build.put(x, 2, z, style.glass, {
                "north": "false", "south": "false", "east": "true", "west": "true",
                "waterlogged": "false"})

    # A light either side of the door, under the eaves, the way a village house has them.
    for z in (DOOR_Z - 1, DOOR_Z + 1):
        build.put(LOW_X - 1, 2, z, "minecraft:wall_torch", {"facing": "west"})
    # And three inside, high enough to be over the tables rather than in the way of them.
    build.put(middle_x - 2, 3, north, "minecraft:wall_torch", {"facing": "south"})
    build.put(middle_x + 1, 3, north, "minecraft:wall_torch", {"facing": "south"})
    build.put(BEHIND_X, 3, north + 1, "minecraft:wall_torch", {"facing": "west"})

    # The doorstep, so the street has something to meet.
    build.put(LOW_X - 1, 0, DOOR_Z, "minecraft:jigsaw", {"orientation": "west_up"}, nbt={
        "id": "minecraft:jigsaw",
        "name": "minecraft:building_entrance",
        "target": "minecraft:building_entrance",
        "pool": f"minecraft:village/{build.style}/streets",
        "joint": "aligned",
        "final_state": f"{style.step}[facing=east,half=bottom,shape=straight,"
                       "waterlogged=false]",
    })
    # And whoever is going to stand behind the counter - in the gap in it, so they start where
    # they work. This is the piece that makes it a shop rather than a room: vanilla's houses
    # get their villager from exactly this.
    build.put(BEHIND_X, 0, COUNTER_GAP_Z, "minecraft:jigsaw", {"orientation": "up_north"},
              nbt={
                  "id": "minecraft:jigsaw",
                  "name": "minecraft:bottom",
                  "target": "minecraft:bottom",
                  "pool": f"minecraft:village/{build.style}/villagers",
                  "joint": "rollable",
                  "final_state": style.floor,
              })


def shop(build):
    """The counter, the stock behind it, and the two tables."""
    north, south = INNER_Z
    for z in range(north, south + 1):
        if z == COUNTER_GAP_Z:
            # The gap in the counter. Without it the shopkeeper cannot get behind their own
            # till, and a villager who cannot reach their work is a villager who quits.
            continue
        build.put(COUNTER_X, 1, z, "gathering:shop_counter", {"facing": "west"})

    build.put(BEHIND_X, 1, north, "minecraft:chest",
              {"facing": "west", "type": "single", "waterlogged": "false"},
              nbt={"id": "minecraft:chest", "LootTable": CHEST_LOOT})

    # A table is two blocks by two, and each quarter has to say which quarter it is: four
    # blocks all claiming to be the north-west corner are four overlapping tables, not one.
    #
    # Both of them sit one row in from the front and back walls, because the two people a
    # table seats sit across its north and south edges - so those two rows are the chairs.
    front, back = TABLE_Z
    for west, east in TABLES_X:
        for x, z, part in ((west, front, "north_west"), (east, front, "north_east"),
                           (west, back, "south_west"), (east, back, "south_east")):
            build.put(x, 1, z, "gathering:table", {"part": part})


def roof(build, style, base):
    """A hipped roof of stairs, or a flat one with a parapet where the village is desert."""
    if style.flat:
        build.fill(LOW_X, HIGH_X, base, base, LOW_Z, HIGH_Z, style.flat)
        for x in range(LOW_X, HIGH_X + 1):
            for z in range(LOW_Z, HIGH_Z + 1):
                if x in (LOW_X, HIGH_X) or z in (LOW_Z, HIGH_Z):
                    # A wall's sides are low or tall rather than on or off, and its post is
                    # what makes a corner read as a corner.
                    build.put(x, base + 1, z, style.parapet, {
                        "north": "low", "south": "low", "east": "low", "west": "low",
                        "up": "true", "waterlogged": "false"})
        return base + 2

    # Ring by ring, pulling in one block a level, exactly as vanilla's small houses do. The
    # building is longer than it is deep, so the rings run out across before they run out
    # along and the last of them is a ridge rather than a point.
    levels = (min(WIDE, DEEP) - 1) // 2 + 1
    for level in range(levels):
        y = base + level
        west, east = level, WIDE - 1 - level
        north, south = level, DEEP - 1 - level
        ridge = south - north <= 1 or east - west <= 1
        for x in range(WIDE):
            for z in range(DEEP):
                out = inset(x, z)
                if out == level:
                    if ridge:
                        # A one-block-wide top is a ridge beam, not four stairs facing each
                        # other.
                        build.put(x, y, z, style.roof_fill)
                    else:
                        facing, shape = stair_facing(x, z, west, east, north, south)
                        build.put(x, y, z, style.roof, {
                            "facing": facing, "half": "bottom", "shape": shape,
                            "waterlogged": "false"})
                elif out == level + 1:
                    build.put(x, y, z, style.roof_fill)
    return base + levels


def store(style):
    build = Build()
    build.style = style.name
    walls = 4
    shell(build, style, walls)
    shop(build)
    height = roof(build, style, walls)
    return build.tag((WIDE, height, DEEP))


def main():
    os.makedirs(OUT, exist_ok=True)
    for style in STYLES:
        path = f"{OUT}/{style.name}_card_shop.nbt"
        root = store(style)
        nbtio.write(path, root)
        size = root.value["size"].value[1]
        blocks = len(root.value["blocks"].value[1])
        print(f"wrote {path} ({size[0]}x{size[1]}x{size[2]}, {blocks} blocks, "
              f"{len(root.value['palette'].value[1])} states)")


if __name__ == "__main__":
    main()
