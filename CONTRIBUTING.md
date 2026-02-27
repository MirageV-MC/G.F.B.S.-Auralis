# Contributing to GFBS: Auralis

Thank you for your interest in contributing to GFBS: Auralis! We appreciate your time and effort to help improve this project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [About the Project](#about-the-project)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Pull Request Guidelines](#pull-request-guidelines)
- [Coding Standards](#coding-standards)
- [Commit Message Guidelines](#commit-message-guidelines)

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment. Be considerate, welcoming, and constructive in all interactions.

## About the Project

GFBS: Auralis is a Minecraft client-side audio control mod designed for advanced gameplay and content creators. Built on OpenAL, it provides a completely independent audio system separate from the vanilla game, enabling more precise and controllable 3D sound playback and management.

### Key Features

- **Independent Audio Engine** - Does not use the vanilla SoundEngine playback pipeline
- **Accurate 3D Spatial Audio** - Supports world-coordinate sound sources with min/max distance attenuation
- **Stable SoundEvent Parsing** - Consistent resolution of sound-effect IDs to audio resources
- **Powerful Command System** - Fully compatible with command blocks and server consoles
- **Client-Safe Execution** - All OpenAL operations execute only on the client
- **Multi-Source Concurrency** - Source-pool management with buffer caching

### Target Audience

- Map authors / Story-map creators
- Advanced command-block users
- Server developers
- Mod developers requiring independent audio control

## Getting Started

### Prerequisites

- **Java Development Kit (JDK) 17** or higher
- **Gradle 8.x** (wrapper included)
- **IDE** with Minecraft mod development support (IntelliJ IDEA recommended)
- Basic knowledge of Minecraft Forge modding and OpenAL

### Fork and Clone

1. Fork the repository on GitHub
2. Clone your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/G.F.B.S.-Auralis.git
   cd G.F.B.S.-Auralis
   ```
3. Add the upstream repository:
   ```bash
   git remote add upstream https://github.com/MirageV-MC/G.F.B.S.-Auralis.git
   ```

## Development Setup

### Building the Project

```bash
# Windows
.\gradlew build

# Linux/macOS
./gradlew build
```

### Setting Up Your IDE

#### IntelliJ IDEA (Recommended)

```bash
# Windows
.\gradlew genIntellijRuns

# Linux/macOS
./gradlew genIntellijRuns
```

After running the command, open the project in IntelliJ IDEA and refresh the Gradle project.

#### Eclipse

```bash
# Windows
.\gradlew genEclipseRuns

# Linux/macOS
./gradlew genEclipseRuns
```

### Running the Game

```bash
# Run client
.\gradlew runClient

# Run server
.\gradlew runServer
```

### Important Notes

- This is a **client-side mod** - the server is only responsible for commands and network synchronization
- All OpenAL operations execute only on the client side
- Ensure the client supports OpenAL before use (Minecraft satisfies this by default)

## How to Contribute

### Reporting Bugs

If you find a bug, please open an issue with the following information:

- **Description**: A clear description of the bug
- **Steps to Reproduce**: Detailed steps to reproduce the behavior
- **Expected Behavior**: What you expected to happen
- **Actual Behavior**: What actually happened
- **Environment**: Minecraft version, Forge version, and other relevant mods
- **Logs**: Relevant log files or crash reports (use code blocks or paste services)

### Suggesting Features

We welcome feature suggestions! Please open an issue with:

- **Description**: A clear description of the feature
- **Use Case**: Why this feature would be useful
- **Implementation Ideas**: Optional technical details if you have any

### Submitting Code Changes

1. Create a new branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. Make your changes
3. Test your changes thoroughly in-game
4. Commit your changes with clear messages
5. Push to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```
6. Open a Pull Request

## Pull Request Guidelines

- **One Feature Per PR**: Keep pull requests focused on a single feature or bug fix
- **Descriptive Title**: Use a clear, descriptive title
- **Description**: Explain what changes you made and why
- **Reference Issues**: Link to any related issues
- **Tests**: Ensure your code compiles and runs correctly in-game
- **Documentation**: Update documentation if needed

### PR Checklist

- [ ] Code compiles without errors
- [ ] Changes have been tested in-game (client-side)
- [ ] Commit messages follow the guidelines
- [ ] Code follows the project's coding standards
- [ ] No unnecessary changes or formatting
- [ ] OpenAL operations remain client-side only

## Coding Standards

### Java Code Style

- Use **4 spaces** for indentation (no tabs)
- Use **UTF-8** encoding
- Follow standard Java naming conventions:
  - `PascalCase` for classes and interfaces
  - `camelCase` for methods and variables
  - `UPPER_SNAKE_CASE` for constants
- Keep methods focused and reasonably sized
- Add Javadoc comments for public APIs

### Project-Specific Guidelines

- Follow the existing package structure under `org.mirage.gfbs.auralis`
- Use appropriate logging levels via Forge's logging system
- Ensure Mixin classes are properly annotated
- Maintain backwards compatibility when modifying APIs
- **Keep OpenAL operations on the client side only**
- Server code should only send control instructions via network packets

### Code Organization

```
src/main/java/org/mirage/gfbs/auralis/
├── api/          # Public API interfaces and classes
├── command/      # Command handlers
├── event/        # Event handlers
├── network/      # Network packets and handlers
├── server/       # Server-side logic (commands & sync only)
├── utils/        # Utility classes
└── *.java        # Core classes (engine, sound controller, etc.)
```

## Commit Message Guidelines

We follow a conventional commit style:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Types

- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or modifying tests
- `chore`: Maintenance tasks

### Examples

```
feat(api): add sound fade-in/out support
fix(network): resolve packet desync on world change
docs(readme): update installation instructions
refactor(engine): optimize OpenAL source pool management
```

## License

By contributing to this project, you agree that your contributions will be licensed under the [MIT License](LICENSE).

---

Thank you for contributing to GFBS: Auralis! 🎵
