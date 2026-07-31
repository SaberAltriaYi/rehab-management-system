FROM eclipse-temurin:8u492-b09-jre-jammy@sha256:2dd44832214038e70ecda102295e1099012a2a665fe5c81167c3eb7782085fed

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fonts-wqy-zenhei \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app \
    && mkdir -p /app/data/rehab /app/logs \
    && chown -R app:app /app

WORKDIR /app
COPY --chown=app:app yudao-server.jar /app/app.jar

USER app
ENV TZ=Asia/Shanghai

EXPOSE 48080
ENTRYPOINT ["java", "-Xms256m", "-Xmx768m", "-Djava.security.egd=file:/dev/./urandom", "-Dsun.io.useCanonCaches=false", "-jar", "/app/app.jar"]
