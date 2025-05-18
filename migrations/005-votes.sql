DROP TABLE IF EXISTS translation_votes;

CREATE TABLE translation_votes
  ( translation_id integer REFERENCES translations ( translation_id )
                           ON DELETE CASCADE
  , user_id        integer REFERENCES users ( user_id ) ON DELETE CASCADE
  , is_in_favor    boolean NOT NULL
  , PRIMARY KEY ( translation_id , user_id )
  );
