/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.image;

import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.cv.ImageCV;
import org.weasis.core.api.image.cv.ImageProcessor;
import org.weasis.core.api.media.data.PlanarImage;

public class BrightnessOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrightnessOp.class);

    public static final String OP_NAME = "rescale"; //$NON-NLS-1$

    public static final String P_BRIGTNESS_VALUE = "rescale.brightness"; //$NON-NLS-1$
    public static final String P_CONTRAST_VALUE = "rescale.contrast"; //$NON-NLS-1$

    public BrightnessOp() {
        setName(OP_NAME);
    }

    public BrightnessOp(BrightnessOp op) {
        super(op);
    }

    @Override
    public BrightnessOp copy() {
        return new BrightnessOp(this);
    }

    @Override
    public void process() throws Exception {
        PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
        PlanarImage result = source;

        Double contrast = (Double) params.get(P_CONTRAST_VALUE);
        Double brigtness = (Double) params.get(P_BRIGTNESS_VALUE);

        if (contrast != null && brigtness != null) {
            result = ImageProcessor.rescaleToByte(ImageCV.toImageCV(source), contrast / 100.0, brigtness);
        }

        params.put(Param.OUTPUT_IMG, result);
    }

}