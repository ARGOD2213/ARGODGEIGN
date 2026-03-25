# Local Predictive Intelligence Training Sources

This project now uses a local predictive scoring path instead of paid runtime inference APIs for machine intelligence.

Public references used to shape the bundled seed profiles:

- UCI AI4I 2020 Predictive Maintenance Dataset
  https://archive.ics.uci.edu/dataset/601/predictive%2Bmaintenance%2Bdata
- NASA C-MAPSS Aircraft Engine Simulator Data
  https://data.nasa.gov/dataset/c-mapss-aircraft-engine-simulator-data

Implementation note:

- To control AWS cost and avoid runtime internet dependencies, the application does not download these datasets at startup.
- Instead, it uses bundled seed profiles derived from the public dataset characteristics and expands them locally into a larger synthetic training library for in-process KNN-style scoring.
- The resulting model is advisory only and is not a certified control or safety system.
