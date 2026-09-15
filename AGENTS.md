## Code Style

### Null Safety

- Use Jspecify library for null safety
- Apply `@NullMarked` for each package-info, never use `@NonNull`
- Use `@Nullable` explicitly for parameters/returns that may be null
- Never accept null where a meaningful default exists
- Never check nullability when a nonnull value is passed

### Exception Handling

- Extend `RuntimeException` for unchecked exceptions
- For exception classes, include `@Serial` with `serialVersionUID` set to `YYYYMMDD00` format
- Provide descriptive exception messages with context

### Record Usage

- Use records for immutable data carriers
- Validate constructor parameters in compact constructor.

### Thread Safety

- Document thread-safety guarantees in class Javadoc

### Resource Management

- Implement `AutoCloseable` for resources that need explicit cleanup

### API Design Principles

- **Factory over constructors**: When necessary, provide static factory methods like `open()`.
- **Fluent defaults**: Use `default` methods for common operations
- **Immutable by default**: Records and value objects
- **Composition over inheritance**: Use interfaces for public APIs
- **Single responsibility**: Each class has one clear purpose

### Documentation

- You need not write Javadocs
- You can write in-line comments if the logic here is tricky or hard to understand

### Constants

- Use `UPPER_SNAKE_CASE` for final fields
- Define magic numbers as named constants with documentation

### Performance Considerations

- Avoid unnecessary object allocation in hot paths
- Use primitive types where appropriate
- Document computational complexity for critical methods

### Other Formatting Hints

- No blank lines after class definitions
- Meaningful blank lines between code blocks. Do not write a bulk of code.

If you'd read these principles, say "Got it."