# Arena maps

Put the **custom "Love Fight" arena map** here as a zip named after the world:

```
maps/ngnl_arena_city.zip
```

* Zip the **world folder** of your arena (the folder that contains `level.dat` and `region/`).
  Either the files directly at the root of the zip or wrapped in one folder both work.
* `release` bundles this folder into the server package (`plugins/NoGameNoLifeUHC/maps/`), and the
  plugin extracts the map into the server the first time the arena is needed.
* To embed the map **inside the plugin jar** instead, put the zip in `src/main/resources/maps/`
  (keep it below ~90 MB, GitHub's file limit; for bigger maps attach the zip to the release).
* The world name must match `arena.world-name` in `config.yml` (default `ngnl_arena_city`).
* No map? Nothing to do: a city of towers is generated automatically.
