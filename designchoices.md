# Trace App: Design Language & Choices

## 1. Core Visual Identity
- **Theme Colors**: The primary accent color has been shifted from warm orange to a vibrant, fresh **Slime Blue / Cyan** (`#4DB6AC`). Dark mode uses deep grays (`#151515`, `#20201F`) for a cohesive, modern contrast.
- **Glassmorphism**: UI elements like the attachment panels, search fields, and sidebars utilize frosted glass aesthetics (translucent background colors combined with subtle borders) to blend cleanly with the background.
- **Edge-to-Edge Layout**: Sidebars and main panels span the full length of the screen, opting for `RectangleShape` over rounded corners in drawer sheets for a sleeker, borderless feel.

## 2. Mascot: The Fantasy Slime
- **Form & Shape**: The mascot has evolved from a static orb to a dynamic fantasy slime. Using animated `RoundedCornerShape` percentages, the slime maintains a perfectly round top and a flatter, squishier base that constantly breathes and slightly morphs.
- **Glossy Volume**: Instead of a flat color, the slime uses a **reversed radial gradient**. The core is a deep dark teal (`#00838F`), which smoothly fades outwards into the primary cyan, and ends in a glowing light mint edge (`#E0F7FA`), giving it a 3D gelatinous/water-bubble look.
- **Organic Fluidity**: 
  - **Random Shape Shifting**: Every few seconds, the slime randomly transitions between a tall, bouncy blob and a flatter, relaxed puddle.
  - **Contextual Placement**: When the user opens the keyboard to type, the slime glides down to rest just above the input field, acting as an active listener.
  - **Reactivity**: Tapping interactive UI elements (like opening the attachment menu or sending a message) triggers a quick wink animation, making the mascot feel alive and responsive.

## 3. UI Micro-interactions
- UI components (like the main screen layout and text input) use `animateContentSize()` to fluidly expand and shift elements instead of snapping abruptly when the keyboard appears or when text wraps to a new line.
- Toggle switches have been slightly scaled down (`0.78f`) to keep the interface un-cluttered and modern.
- The keyboard enter button defaults to adding a new line, keeping message submission intentional via the dedicated send button.
