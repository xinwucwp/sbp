## Fault salt bounary interpretation with optimal path picking

This repository contains computer programs written and used by 
[Xinming Wu](http://www.jsg.utexas.edu/wu/) 
for salt boundary interpretation that is discussed in our Geophysics paper 
[Fault salt bounary interpretation with optimal path picking]
(http://www.jsg.utexas.edu/wu/files/wu2018FastSaltBoundaryInterpretationWithOptimalPathPicking.pdf).

If you find this work helpful in your research, please cite:

    @article{doi:wu2018fast,
        author = {Xinming Wu and Sergey Fomel and Michael Hudec},
        title = {Fast salt boundary interpretation with optimal path picking},
        journal = {GEOPHYSICS},
        volume = {83},
        issue = {3},
        pages = {O45-O53},
        year = {2018},
        doi = {10.1190/GEO2017-0481.1},
        URL = {https://library.seg.org/doi/abs/10.1190/geo2017-0481.1},
    }

Please use gradle to build the codes.
All the dependent libraries have been included in the ./libs folder.

### Summary

Here are brief descriptions of key components:

#### OptimalPathPicker
Implements the algorithm of optimal path picking

#### SaltBoundaryPicker2
Implements the interactive 2D salt boundary picker

#### run a test
cd sbp/

type ./j sbp.SaltBoundaryPicker2 to run the 2D demo


---
Copyright (c) 2018, Xinming Wu. All rights reserved.
This software and accompanying materials are made available under the terms of
the [Common Public License - v1.0](http://www.eclipse.org/legal/cpl-v10.html),
which accompanies this distribution.
