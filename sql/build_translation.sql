CREATE OR REPLACE FUNCTION build_translation ( pid integer , lid integer )
RETURNS SETOF json
AS $$
BEGIN
  RETURN QUERY SELECT json_object_agg_unique_strict
                        ( string_name , translation_text )
                 FROM strings
                 JOIN translations USING ( string_id )
                 JOIN translation_approvals USING ( translation_id )
                WHERE ( project_id , lang_id ) = ( pid , lid );
END;
$$ LANGUAGE plpgsql;
