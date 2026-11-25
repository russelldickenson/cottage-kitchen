"""Compose the tracked in-game dish artwork into a README showcase board."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
SPRITES = ROOT / "app/src/main/res/drawable-nodpi"
OUT = Path(__file__).resolve().parents[1] / "dishes.png"

DISHES = [
    ("dish_strawberry_jam", "Strawberry Jam"),
    ("dish_apple_sauce", "Apple Sauce"),
    ("dish_marmalade", "Orange Marmalade"),
    ("dish_melon_sorbet", "Melon Sorbet"),
    ("dish_pancakes", "Pancakes"),
    ("dish_vanilla_ice_cream", "Vanilla Ice Cream"),
    ("dish_blueberry_muffin", "Blueberry Muffins"),
    ("dish_pumpkin_soup", "Pumpkin Soup"),
    ("dish_corn_chowder", "Corn Chowder"),
    ("dish_fruit_salad", "Fruit Salad"),
    ("dish_apple_pie", "Apple Pie"),
    ("dish_birthday_cake", "Birthday Cake"),
    ("dish_honey_cake", "Honey Cake"),
    ("dish_granola_bars", "Granola Bars"),
]

CREAM = "#FFF4D2"
PANEL = "#FFFAEC"
EDGE = "#E2C99C"
INK = "#5A3410"
WOOD = "#8B5A2B"

W, H = 1200, 850
CELL_W, CELL_H = 220, 225
ROW_Y = [155, 380, 605]


def font(size, bold=True):
    name = "Arial Rounded Bold.ttf" if bold else "Arial.ttf"
    return ImageFont.truetype(f"/System/Library/Fonts/Supplemental/{name}", size)


def centered(draw, text, cx, baseline, fnt, fill, max_width):
    box = draw.textbbox((0, 0), text, font=fnt)
    if box[2] - box[0] <= max_width:
        draw.text((cx - (box[2] - box[0]) / 2, baseline), text, font=fnt, fill=fill)
        return
    words = text.split()
    pivot = min(range(1, len(words)), key=lambda i: abs(len(" ".join(words[:i])) - len(" ".join(words[i:]))))
    lines = [" ".join(words[:pivot]), " ".join(words[pivot:])]
    fnt = font(19)
    for i, line in enumerate(lines):
        box = draw.textbbox((0, 0), line, font=fnt)
        draw.text((cx - (box[2] - box[0]) / 2, baseline + i * 23), line, font=fnt, fill=fill)


def main():
    img = Image.new("RGB", (W, H), CREAM)
    draw = ImageDraw.Draw(img)

    draw.text((60, 46), "14 dishes to cook", font=font(48), fill=INK)
    draw.text(
        (60, 110),
        "Every dish is cooked across several levels — collect its ingredients one by one.",
        font=font(24, bold=False),
        fill=WOOD,
    )

    for index, (key, name) in enumerate(DISHES):
        row, col = divmod(index, 5)
        x = 60 + col * CELL_W if row < 2 else 160 + col * CELL_W
        y = ROW_Y[row]

        draw.rounded_rectangle((x + 8, y, x + CELL_W - 8, y + 210), radius=18, fill=PANEL, outline=EDGE, width=2)

        art = Image.open(SPRITES / f"{key}.png").convert("RGBA")
        art = art.resize((140, 140), Image.Resampling.LANCZOS)
        img.paste(art, (x + (CELL_W - 140) // 2, y + 10), art)

        centered(draw, name, x + CELL_W / 2, y + 180, font(20), WOOD, 190)

    img.save(OUT, optimize=True)
    print(f"wrote {OUT} ({img.width}x{img.height})")


if __name__ == "__main__":
    main()
