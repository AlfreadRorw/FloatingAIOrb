ALF Vision Panel - Gradle Fix

Copy these files into the ROOT of the FloatingAIOrb repository:

1. gradlew
2. gradlew.bat
3. .github/workflows/build.yml

This project uses Gradle 8.9 provided by GitHub Actions through:
gradle/actions/setup-gradle@v4

After copying:
chmod +x gradlew
git add gradlew gradlew.bat .github/workflows/build.yml
git commit -m "fix: add Gradle launcher and build workflow"
git push origin master
