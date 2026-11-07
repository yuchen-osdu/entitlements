FROM alpine/curl:8.22.0

WORKDIR /opt

RUN apk upgrade --no-cache && apk add --no-cache jq bash

COPY ./entitlements-v2-core-plus/bootstrap/ /opt/

RUN chmod +x bootstrap.sh validate-env.sh

RUN addgroup -g 10001 -S nonroot \
  && adduser -h /opt -G nonroot -S -u 10001 nonroot

RUN chown -R 10001:10001 /opt

USER 10001:10001

CMD ["/bin/bash", "-c", "/opt/bootstrap.sh"]
