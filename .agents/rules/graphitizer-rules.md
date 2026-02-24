# ==========================================
# Antigravity Agent Rules for Graphitizer
# ==========================================

## 1. Environment & Shell Context
- Operating System: Windows
- Terminal: PowerShell
- Java Version: JDK 25
- Build Tool: Local Maven installation

## 2. Terminal Initialization
If you are opening a new terminal session, you must initialize the Java environment before running Maven commands:
`$env:JAVA_HOME="C:\Program Files\Java\jdk-25"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH";`

## 3. Maven Execution & Pathing (CRITICAL)
- ALWAYS use the specific local Maven path: `.\apache-maven-3.9.6\bin\mvn.cmd`. Do not use `mvn` or `./mvnw`.
- The main class (`com.verifiedlogic.graphitizer.GraphitizerApp`) is permanently configured in the `pom.xml` via the `exec-maven-plugin`.
- To build and run the app, strictly use: `.\apache-maven-3.9.6\bin\mvn.cmd clean compile exec:java`
- NEVER append `-Dexec.mainClass` or any other `-D` arguments when running the application.

## 4. PowerShell Argument Parsing
- If you absolutely MUST pass a `-D` flag to Maven (e.g., for running a specific test), you MUST insert the PowerShell stop-parsing operator (`--%`) immediately before the flags.
- Correct Example: `.\apache-maven-3.9.6\bin\mvn.cmd test --% -Dtest=MyTest`
- Do not attempt to use quotation marks to escape `-D` flags, as PowerShell will still parse them incorrectly.
