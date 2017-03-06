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
import org.weasis.core.api.Messages;
import org.weasis.core.api.image.cv.ImageCV;
import org.weasis.core.api.image.cv.ImageProcessor;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.PlanarImage;

public class AutoLevelsOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoLevelsOp.class);

    public static final String OP_NAME = Messages.getString("AutoLevelsOp.auto_ct"); //$NON-NLS-1$
    public static final String P_IMAGE_ELEMENT = "img.element"; //$NON-NLS-1$
    /**
     * Set whether auto levels is applied to the image (Required parameter).
     *
     * Boolean value.
     */
    public static final String P_AUTO_LEVEL = "auto.level"; //$NON-NLS-1$

    public AutoLevelsOp() {
        setName(OP_NAME);
    }

    public AutoLevelsOp(AutoLevelsOp op) {
        super(op);
    }

    @Override
    public AutoLevelsOp copy() {
        return new AutoLevelsOp(this);
    }

    @Override
    public void process() throws Exception {
        ImageElement imageElement = (ImageElement) params.get(P_IMAGE_ELEMENT);
        PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
        PlanarImage result = source;
        Boolean auto = (Boolean) params.get(P_AUTO_LEVEL);

        if (auto != null && auto && imageElement != null) {
            double min = imageElement.getMinValue(null, true);
            double max = imageElement.getMaxValue(null, true);
            double slope = 255.0 / (max -min);
            double yint = 255.0 - slope * max;
            result = ImageProcessor.rescaleToByte(ImageCV.toImageCV(source), slope, yint);
        }

        params.put(Param.OUTPUT_IMG, result);
    }

}