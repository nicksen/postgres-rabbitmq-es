-- Streams schema

# --- !Ups

CREATE TABLE streams (
  stream_id VARCHAR(255) PRIMARY KEY,
  commit_id BIGINT NOT NULL REFERENCES commits ON DELETE RESTRICT
);


# --- !Downs

DROP TABLE streams;
