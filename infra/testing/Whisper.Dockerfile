FROM debian:bookworm-slim AS builder
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates git cmake g++ make && rm -rf /var/lib/apt/lists/*
RUN git clone --depth 1 --branch v1.7.6 https://github.com/ggml-org/whisper.cpp.git /src
RUN cmake -S /src -B /src/build -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF -DWHISPER_BUILD_TESTS=OFF && cmake --build /src/build --target whisper-cli -j2
FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends python3 ffmpeg libgomp1 libstdc++6 && rm -rf /var/lib/apt/lists/* && useradd --uid 10001 runner
COPY --from=builder /src/build/bin/whisper-cli /usr/local/bin/whisper-cli
COPY infra/testing/whisper_runner.py /opt/whisper_runner.py
USER 10001:10001
ENTRYPOINT ["python3", "-B", "/opt/whisper_runner.py"]
