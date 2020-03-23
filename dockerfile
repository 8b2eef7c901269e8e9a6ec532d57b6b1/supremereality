FROM ubuntu:18.04
ADD . home/supremereality
WORKDIR home/supremereality
RUN apt update && apt install -y default-jre clojure leiningen && lein uberjar
CMD ["java", "-jar", "target/supremereality-0.2.4-standalone.jar"]
MAINTAINER jaredkschreiber@gmail.com
EXPOSE 3000