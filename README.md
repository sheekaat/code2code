# code2code (Java + Google Gemini)

AI-powered code converter supporting Tibco BW, .NET, and VB6 migration. Built with **Spring Boot** and **Google Gemini AI**.

## Overview

This is a Java port of the Python-based code conversion agent, using Google's Agent Development Kit (ADK) for agent orchestration and Gemini AI for code generation.

## Features

- **Multi-source code conversion**: Tibco BW, .NET (C#), VB6
- **Target platforms**: Java with Spring Boot, React JS with Spring Boot
- **RAG (Retrieval-Augmented Generation)** with ChromaDB vector store
- **Container-ready** deployment with Docker
- **Web UI** for easy code migration
- **Pattern matching** from knowledge base

## Architecture

```
┌─────────────────┐
│   Web UI        │
│  (Browser)      │
└────────┬────────┘
         │
┌────────▼────────┐
│ Spring Boot     │
│  Application    │
└────────┬────────┘
         │
    ┌────┴────┬─────────────┐
    ▼         ▼             ▼
┌────────┐ ┌──────────┐ ┌────────────┐
│ File   │ │ Pattern  │ │ Repository │
│Converter│ │ Matcher  │ │Orchestrator│
└────┬───┘ └────┬─────┘ └─────┬──────┘
     │          │             │
     ▼          ▼             ▼
┌──────────────────────────────────┐
│         Gemini AI (LLM)          │
└──────────────────────────────────┘
```

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker (optional, for containerized deployment)
- Google Gemini API key ([Get one here](https://aistudio.google.com/app/apikey))

### 1. Clone and Build

```bash
cd code2code
mvn clean compile
```

### 2. Configure Application

Edit `src/main/resources/application.properties`:

```properties
# Google Gemini API Key
# Get your key at: https://aistudio.google.com/app/apikey
google.api.key=your_api_key_here

# LLM Configuration (optional)
gemini.model=gemini-2.5-flash
llm.routing.tier=balanced

# ChromaDB Configuration (for RAG)
chroma.host=localhost
chroma.port=8000
chroma.collection=dotnet_java_patterns

# Conversion Settings (optional)
max.retries=3
context.budget=128000
enable.observability=true
```

### 3. Run Application

```bash
# Build and run
mvn clean package -DskipTests
java -jar target/code2code-1.0-SNAPSHOT.jar
```

Then open http://localhost:8081 in your browser.

## Container Deployment

### Using Docker Compose (Recommended)

```bash
# Set your API key in application.properties first
# Then start with ChromaDB
docker-compose up -d

# Access the web UI at http://localhost:8081
```

### Using Docker Only

```bash
# Build image
docker build -t code2code-converter .

# Run with API key from application.properties
# Mount the properties file:
docker run -v $(pwd)/src/main/resources/application.properties:/app/application.properties \
  -p 8081:8081 \
  code2code-converter
```

## Project Structure

```
code2code/
├── pom.xml                          # Maven configuration
├── Dockerfile                       # Container definition
├── docker-compose.yml               # Full stack with ChromaDB
├── src/
│   └── main/
│       ├── java/com/code2code/
│       │   ├── config/
│       │   │   └── AppConfig.java       # Configuration management
│       │   ├── web/
│       │   │   └── ConversionController.java  # Web UI controller
│       │   ├── service/
│       │   │   ├── GeminiApiClient.java     # Gemini AI client
│       │   │   ├── ChunkedConverter.java      # Chunked conversion
│       │   │   ├── CodebaseAnalyzer.java      # Codebase analysis
│       │   │   ├── PatternExtractor.java      # Pattern extraction
│       │   │   └── converter/
│       │   │       ├── FormToReactConverter.java  # VB6 to React
│       │   │       └── ReactComponentGenerator.java
│       │   └── Code2CodeApp.java        # Spring Boot application
│       └── resources/
│           ├── application.properties     # Configuration
│           └── static/
│               └── index.html             # Web UI
└── .github/workflows/
    └── ci.yml                       # CI/CD pipeline
```

## Supported Languages

| Source | Target | Notes |
|--------|--------|-------|
| Tibco BW | Java with Spring Boot | Direct conversion |
| .NET (C#) | Java with Spring Boot | Direct conversion |
| VB6 | Java with Spring Boot | Direct conversion |
| VB6 | React JS with Spring Boot + WinForms | Higher accuracy via WinForms intermediate |

## Technology Mappings

| Source | Target |
|--------|--------|
| C# / VB.NET / F# | Java 17+ |
| .NET Framework | Spring Boot |
| LINQ | Java Streams API |
| Entity Framework | Spring Data JPA |
| ASP.NET | Spring Web / MVC |
| MSMQ / Tibco EMS | Google Pub/Sub / RabbitMQ |
| SQL Server / IBM DB2 | PostgreSQL / AlloyDB |
| async/await | CompletableFuture |
| NuGet | Maven / Gradle |
| VB6 Forms | React Components |

## Extending for Automated Pipeline

To build an automated CI/CD pipeline for .NET to Java conversion:

### 1. GitHub Actions Workflow

Add to `.github/workflows/convert.yml`:

```yaml
name: Auto .NET to Java Conversion

on:
  push:
    paths:
      - 'dotnet-src/**'

jobs:
  convert:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Download converter
        run: |
          curl -L -o converter.jar \
            https://github.com/yourorg/releases/download/v1.0/converter.jar
      
      - name: Run conversion
        env:
          GOOGLE_API_KEY: ${{ secrets.GOOGLE_API_KEY }}
        run: |
          java -jar converter.jar ./dotnet-src/ ./java-output/
      
      - name: Create PR with converted code
        uses: peter-evans/create-pull-request@v5
        with:
          title: 'Auto-convert .NET changes to Java'
          branch: auto-convert-${{ github.run_id }}
```

### 2. GitLab CI/CD

```yaml
stages:
  - convert
  - test
  - deploy

convert:
  stage: convert
  image: eclipse-temurin:17-jre
  script:
    - java -jar converter.jar $CI_PROJECT_DIR/dotnet $CI_PROJECT_DIR/java
  artifacts:
    paths:
      - java/
```

### 3. Azure DevOps Pipeline

```yaml
trigger:
  paths:
    include:
      - dotnet-src/*

steps:
- task: JavaToolInstaller@0
  inputs:
    versionSpec: '17'
    jdkArchitecture: 'x64'

- script: |
    java -jar converter.jar $(Build.SourcesDirectory)/dotnet-src \
      $(Build.SourcesDirectory)/java-output
  env:
    GOOGLE_API_KEY: $(GOOGLE_API_KEY)
```

## Configuration

### Application Properties

Edit `src/main/resources/application.properties`:

| Property | Description | Default |
|----------|-------------|---------|
| `google.api.key` | Gemini API key (required) | - |
| `gemini.model` | Model to use | gemini-2.5-flash |
| `llm.routing.tier` | LLM routing tier | balanced |
| `chroma.host` | ChromaDB host | localhost |
| `chroma.port` | ChromaDB port | 8000 |
| `chroma.collection` | ChromaDB collection | dotnet_java_patterns |
| `max.retries` | Retry attempts | 3 |
| `context.budget` | Token limit | 128000 |
| `enable.observability` | Enable observability | true |

### Maven Commands

```bash
# Compile
mvn compile

# Run tests
mvn test

# Package JAR
mvn package

# Run Spring Boot app
mvn spring-boot:run
```

## Web UI Usage

1. Open http://localhost:8081 after starting the application
2. Select source language (Tibco BW, .NET, or VB6)
3. Select target language based on source
4. Upload your source files
5. Click "Start Conversion"
6. Download converted files from the output

## Troubleshooting

### No Gemini API Key
Get your API key at [Google AI Studio](https://aistudio.google.com/app/apikey)

### ChromaDB Connection Issues
```bash
# Check ChromaDB is running
curl http://localhost:8000/api/v1/heartbeat

# Restart with docker-compose
docker-compose restart chromadb
```

### Memory Issues with Large Repos
Increase JVM heap:
```bash
export MAVEN_OPTS="-Xmx4g"
mvn exec:java ...
```

## License

MIT License - see LICENSE file

## Contributing

1. Fork the repository
2. Create a feature branch
3. Submit a pull request

## Support

For issues and questions, please open a GitHub issue.
