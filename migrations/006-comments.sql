DROP TABLE IF EXISTS string_comments;

CREATE TABLE string_comments
  ( comment_id   integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY
  , string_id    integer NOT NULL
                         REFERENCES strings ( string_id ) ON DELETE CASCADE
  , user_id      integer NOT NULL REFERENCES users ( user_id )
  , comment_text text NOT NULL
  , posted_at    timestamptz NOT NULL DEFAULT NOW()
  );

CREATE INDEX ON string_comments ( string_id );
