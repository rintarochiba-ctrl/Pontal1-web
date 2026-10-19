# ビルド段階: コンテナ内で war を作成（手元の target/ 不要）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package \
    && mv target/pontal-web-*.war /build/app.war

# 実行段階: JRE だけで起動（JSPを使うので、jarではなくwarを実行する）
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/app.war app.war
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.war"]
