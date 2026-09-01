# Drawing a mana symbol

A symbol is a **badge** with a **mark** pressed into it, kept apart on purpose. The badge is
the lit sphere and its colour; the mark is the shape cut out of it. That split is what lets
one sun sit on six different badges, and lets you redraw the sun without touching any of them.

```
art/mana/badges/<symbol>.png    the sphere. One per symbol, hybrids already cut.
art/mana/symbols/<mark>.png     the shape. This is the part you draw.
art/mana/guide.png              a template to draw it on top of.
art/mana/ink.json               what colour each badge cuts its marks in.
```

## The short version

Open `guide.png`, draw your shape on a layer over it, save it as
`art/mana/symbols/<name>.png`, and run:

```
python3 tools/mana_art.py
```

That is the whole loop. Everything below is why.

## The canvas

**33×33, and the middle pixel is (16, 16).** The badge's disc is 31 across, so it has a real
middle pixel rather than a seam between two — which is the point of the odd number, and the
reason there is a single pixel to draw around at all.

`guide.png` shows all of it: the **magenta pixel** is the middle, the **cyan crosshair** is
the row and column through it, the **white field** is the room a mark has, and the **red ring**
is the badge's dark rim. A mark drawn over the rim loses its edge against it.

## Where your mark lands

**Wherever you draw it in the canvas — it gets centred for you.** The assembler finds your
mark's own middle and stands it on the badge's, so you do not have to line anything up by
hand. Draw it in the corner if you like.

**Draw it an odd number of pixels wide and tall** and it lands exactly on the middle pixel.
An even one has no middle of its own, so it straddles that pixel with the extra column or row
to the right and below. That is a rule, not a guess, and it is half a source pixel from the
ideal — these are drawn at 33 and shown at 9, so half a pixel here is about an eighth of one
on screen. Nobody will see it. Odd is nicer to draw around; even is fine.

To see where every mark currently stands:

```
python3 tools/mana_art.py --marks
```

## Colour

**There isn't any.** A mark is a silhouette: only its alpha is read, and the badge decides
what colour to cut it in (`ink.json`). Draw in whatever colour you can see against the guide —
black is easiest. A soft edge stays soft, because coverage carries through.

## Hybrids

A two-colour symbol wears two half marks instead of one:

```
art/mana/symbols/half-tl/<mark>.png    the top-left half
art/mana/symbols/half-br/<mark>.png    the bottom-right half
```

These are **not** centred — they sit in their own half of the badge, so the middle pixel is
not what they line up against. Draw them where they go.

## When you have drawn one

```
python3 tools/mana_art.py            # rebuild the font textures and mana.json
python3 tools/mana_art.py --check    # what ships matches the parts on disk
```

`--check` runs in `tools/smoke.sh`, so a mark that was repainted and never rebuilt fails there
rather than quietly shipping the old picture.

`tools/orb_grow.py` is a one-off that already ran — it is what gave the badges their middle
pixel. There is no reason to run it again.
