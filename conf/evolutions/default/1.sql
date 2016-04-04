-- Commits schema

# --- !Ups

CREATE TABLE commits (
  id             BIGSERIAL PRIMARY KEY,
  store_revision BIGINT NOT NULL,
  data           JSON   NOT NULL
);


# --- !Downs

DROP TABLE commits;
