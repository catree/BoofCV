/*
 * Copyright (c) 2011-2016, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.sfm.d3.direct;

import boofcv.abst.sfm.ImagePixelTo3D;
import boofcv.alg.filter.derivative.GImageDerivativeOps;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;
import boofcv.struct.pyramid.ImagePyramid;
import georegression.struct.se.Se3_F32;

/**
 * TODO write
 *
 * @author Peter Abeles
 */
public class PyramidDirectColorDepth<T extends ImageGray<T>> {

	private ImageType<Planar<T>> imageType;

	private ImagePyramid<Planar<T>> pyramid;
	private VisOdomDirectColorDepth<T,?>[] layersOdom;

	private double keyframeFraction = 0.25;

	// number of pixels which were valid in the last layer processed
	private double fractionInBounds = 0;

	private LayerTo3D layerTo3D;

	private Se3_F32 worldToKey = new Se3_F32();
	private Se3_F32 keyToCurrent = new Se3_F32();
	private Se3_F32 work = new Se3_F32();
	private Se3_F32 worldToCurrent = new Se3_F32();

	public PyramidDirectColorDepth(ImagePyramid<Planar<T>> pyramid ) {
		this.pyramid = pyramid;
		imageType = this.pyramid.getImageType();

		layerTo3D = new LayerTo3D();

		layersOdom = new VisOdomDirectColorDepth[pyramid.getNumLayers()];
		for (int i = 0; i < layersOdom.length; i++) {
			ImageType derivType = GImageDerivativeOps.getDerivativeType( imageType );
			layersOdom[i] = new VisOdomDirectColorDepth(imageType.getNumBands(),imageType.getImageClass(), derivType.getImageClass());
		}
	}

	public void setCameraParameters(float fx , float fy ,
									float cx , float cy ,
									int width , int height )
	{
		pyramid.initialize(width, height);
		for (int layer = 0; layer < layersOdom.length; layer++) {
			VisOdomDirectColorDepth o = layersOdom[layer];

			int layerWidth = pyramid.getWidth(layer);
			int layerHeight = pyramid.getHeight(layer);

			float scale = (float)pyramid.getScale(layer);

			o.setCameraParameters(fx/scale,fy/scale,cx/scale,cy/scale,
					layerWidth, layerHeight);
		}
	}

	public boolean process( Planar<T> input , ImagePixelTo3D inputDepth ) {

		if( fractionInBounds == 0 ) {
			setKeyFrame( input, inputDepth );
			fractionInBounds = 1.0;
		} else {
			if( estimateMotion( input ) ) {
				if( fractionInBounds < keyframeFraction ) {
					setKeyFrame( input, inputDepth );
				}
			} else {
				return false;
			}
		}

		return true;
	}

	protected void setKeyFrame( Planar<T> input , ImagePixelTo3D inputDepth) {
		pyramid.process(input);
		layerTo3D.wrap(inputDepth);

		for (int layer = 0; layer < layersOdom.length; layer++) {
			Planar<T> layerImage = pyramid.getLayer(layer);
			layerTo3D.scale = pyramid.getScale(layer);
			layersOdom[layer].setKeyFrame(layerImage,layerTo3D);
		}
		worldToKey.concat(keyToCurrent,work);
		worldToKey.set(work);
		keyToCurrent.reset();
	}

	protected boolean estimateMotion( Planar<T> input ) {
		pyramid.process(input);
		work.set(keyToCurrent);

		boolean oneLayerWorked = false;
		for (int layer = layersOdom.length-1; layer >= 0; layer--) {
//			System.out.println("Layer   "+layer);
			Planar<T> layerImage = pyramid.getLayer(layer);
			VisOdomDirectColorDepth<T,?> o = layersOdom[layer];
			if( o.estimateMotion(layerImage, work) ) {
				oneLayerWorked = true;
				work.set( o.getKeyToCurrent() );
				work.print();

				fractionInBounds = o.getInboundsPixels()/(double)o.getKeyframePixels();
//				System.out.println("   fraction in bounds "+fractionInBounds);
			} else {
//				System.out.println("   failed");
				break;
			}
		}

		if( oneLayerWorked ) {
			keyToCurrent.set(work);
			worldToKey.concat(keyToCurrent,worldToCurrent);
		}

		return oneLayerWorked;
	}

	public boolean isFatalError() {
		return false;
	}

	public Se3_F32 worldToCurrent() {
		return worldToCurrent;
	}

	public ImageType<Planar<T>> getInputType() {
		return imageType;
	}

	public void reset() {
		fractionInBounds = 0;
		keyToCurrent.reset();
		worldToCurrent.reset();
	}

	public void setKeyframeFraction(double keyframeFraction) {
		this.keyframeFraction = keyframeFraction;
	}

	public static class LayerTo3D implements ImagePixelTo3D {
		ImagePixelTo3D orig;

		public double scale;

		public void wrap(ImagePixelTo3D orig) {
			this.orig = orig;
		}

		@Override
		public boolean process(double x, double y) {
			return orig.process((x+0.5)*scale, (y+0.5)*scale);
		}

		@Override
		public double getX() {
			return orig.getX();
		}

		@Override
		public double getY() {
			return orig.getY();
		}

		@Override
		public double getZ() {
			return orig.getZ();
		}

		@Override
		public double getW() {
			return orig.getW();
		}
	}

}
