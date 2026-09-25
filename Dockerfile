# Imagen de la aplicacion. Dos etapas: una compila, la otra ejecuta.
#
# La separacion no es cosmetica. Si se compilara y ejecutara en la misma imagen,
# el contenedor de produccion llevaria Maven, el JDK completo, el codigo fuente y
# el repositorio ~/.m2 entero: cientos de megas de cosas que no hacen falta para
# correr un jar, y cada una es superficie de ataque. Aqui la imagen final solo
# tiene un JRE y el jar.

# ---------------------------------------------------------------------------
# Etapa 1: compilar
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# El pom va solo y primero, antes del codigo. Asi la capa de dependencias se
# cachea: mientras el pom no cambie, un cambio en el codigo no vuelve a bajar
# medio Maven Central. Sin esto, cada build son varios minutos de descargas.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

# Los tests ya los corre el CI en cada pull request, sobre H2 y con el entorno
# limpio. Repetirlos aqui alargaria cada build de imagen sin comprobar nada nuevo.
RUN mvn -B -DskipTests package

# ---------------------------------------------------------------------------
# Etapa 2: ejecutar
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario sin privilegios. Por defecto un contenedor corre como root, y entonces
# cualquier ejecucion de codigo dentro del proceso es root sobre el sistema de
# archivos del contenedor. La aplicacion no necesita escribir nada, asi que
# tampoco necesita permisos para hacerlo.
RUN addgroup -S checkout && adduser -S checkout -G checkout

COPY --from=build /build/target/*.jar app.jar
RUN chown checkout:checkout /app/app.jar

USER checkout

EXPOSE 8080

# Las variables de entorno NO se declaran aqui con valores. Ni DB_PASSWORD ni
# JWT_SECRET: un ENV en el Dockerfile viaja dentro de la imagen y se lee con
# docker history. Las aporta el entorno de ejecucion (compose, ECS, Railway), y
# si falta alguna la aplicacion no arranca a proposito.
#
# DDL_AUTO tampoco se fija: el default de application.properties es validate,
# que es el valor seguro. Quien quiera otro comportamiento lo pide.

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
