# Game Engine & Tile Map Editor

![image](https://github.com/user-attachments/assets/570d5e27-26f2-4b00-9b62-5b8dc0686861)

Uses custom immediate mode UI library. Has a full-fledged tile editor.

## Tile Editor
### Features:
 - Import sprite sheets
 - Auto delete blank tiles
 - Group and ungroup tiles 
 - Add tile collisions
 - Paint tiles with brush
 - Animated tiles
 - Give tiles tags
 - Save and load maps
 - Tile layers
 - Reorder layers, name layers, delete layers etc.
 - Selection fill and delete
### TODO:
 - Fix bugs
 - Ground level tiles
 - Auto tiling
 - Improve workflow

## UI System
### Features:
 - Immediate mode UI
 - Sliders
 - Buttons
 - Labels
 - Lists
### TODO:
 - Checkboxes
 - Nested lists
 - Input cancellation (When input is recieved on a panel/ widget, the input should not be acted upon by any other panel or game feature)


## Engine
### Features:
 - Physics
 - Tile Map Drawing
 - Input (IsKeyPressed, IsMouseDown, etc.)
 - Perlin Noise
 - Fonts
 - World & screen spaces
### TODO:
 - !! Switch to OpenGL for performance. [LWJGL](https://www.lwjgl.org/)
 - Pathfinding
