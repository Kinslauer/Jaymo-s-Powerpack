# fix the server crash at startup from the armillary sphere
- The armillary sphere block was registered without its id, so the server stopped at startup with "Block id not set"
  (`ArmillarySphereBlock.register`). It now sets its id like every other block. Built and started on a dev server.
