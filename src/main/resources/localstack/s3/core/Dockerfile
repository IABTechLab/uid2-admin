FROM nouchka/sqlite3 AS build

COPY ./main.sql /sql/main.sql
COPY ./sites/sites.json /data/sites.json
COPY ./clients/clients.json /data/clients.json
COPY ./keys/keys.json /data/keys.json
COPY ./keysets/keysets.json /data/keysets.json
COPY ./keyset_keys/keyset_keys.json /data/keyset_keys.json
COPY ./client_side_keypairs/client_side_keypairs.json /data/client_side_keypairs.json

RUN ["sqlite3", "/tmp/core.db", "-init", "/sql/main.sql"]

FROM scratch

COPY --from=build /tmp/core.db /core.db