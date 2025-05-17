CREATE OR REPLACE PROCEDURE upload_strings ( pid         integer
                                           , string_data json )
LANGUAGE SQL
AS $$
  MERGE INTO strings AS s
  USING ( SELECT *
            FROM json_each_text ( string_data )
        ) AS j
     ON ( s.project_id , s.string_name ) = ( pid , j.key )
   WHEN MATCHED THEN UPDATE SET string_text = j.value
   WHEN NOT MATCHED BY SOURCE AND s.project_id = pid THEN DELETE
   WHEN NOT MATCHED BY TARGET THEN
        INSERT ( project_id , string_name , string_text )
        VALUES ( pid , j.key , j.value );
$$;
