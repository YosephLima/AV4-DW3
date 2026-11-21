# Estágio 1: compila o .jar com o Maven Wrapper do projeto (mesma versão do Maven usada localmente)
FROM eclipse-temurin:17-jdk AS compilacao
WORKDIR /compilacao
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
# Os testes rodam no CI antes do build da imagem, então aqui só empacota
RUN ./mvnw -B -ntp package -DskipTests

# Estágio 2: imagem final só com o JRE e o .jar, sem código-fonte nem Maven
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S automanager && adduser -S automanager -G automanager
COPY --from=compilacao --chown=automanager:automanager /compilacao/target/automanager-*.jar app.jar
USER automanager
EXPOSE 8080
# JWT_SECRET deve ser passado na execução (docker run -e JWT_SECRET=...). Sem ele, vale o segredo de
# desenvolvimento do application.properties
ENTRYPOINT ["java", "-jar", "app.jar"]
