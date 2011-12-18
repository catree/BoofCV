/*
 * Copyright (c) 2011, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://www.boofcv.org).
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

package boofcv.alg.geo.d2;

import boofcv.abst.feature.tracker.ImagePointTracker;
import boofcv.alg.distort.PixelTransformAffine_F32;
import boofcv.alg.distort.PixelTransformHomography_F32;
import boofcv.alg.geo.AssociatedPair;
import boofcv.alg.geo.d2.stabilization.MosaicImagePointKey;
import boofcv.alg.geo.d2.stabilization.RenderImageMosaic;
import boofcv.core.image.ConvertBufferedImage;
import boofcv.gui.ProcessInput;
import boofcv.gui.VideoProcessAppBase;
import boofcv.gui.feature.VisualizeFeatures;
import boofcv.io.image.SimpleImageSequence;
import boofcv.numerics.fitting.modelset.DistanceFromModel;
import boofcv.numerics.fitting.modelset.ModelFitter;
import boofcv.numerics.fitting.modelset.ModelMatcher;
import boofcv.numerics.fitting.modelset.ransac.SimpleInlierRansac;
import boofcv.struct.FastQueue;
import boofcv.struct.distort.PixelTransform_F32;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageSingleBand;
import georegression.struct.InvertibleTransform;
import georegression.struct.affine.Affine2D_F32;
import georegression.struct.affine.Affine2D_F64;
import georegression.struct.affine.UtilAffine;
import georegression.struct.homo.Homography2D_F32;
import georegression.struct.homo.Homography2D_F64;
import georegression.struct.homo.UtilHomography;
import georegression.struct.point.Point2D_F32;
import georegression.transform.homo.HomographyPointOps;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Parent for applications which distort the input image using extracted point features
 *
 * @author Peter Abeles
 */
public abstract class ImageDistortBaseApp <I extends ImageSingleBand, D extends ImageSingleBand , 
		T extends InvertibleTransform>
		extends VideoProcessAppBase<I,D> implements ProcessInput
{
	private final static int outputBorder = 10;
	
	protected ImagePointTracker<I> tracker;
	protected ModelMatcher<T,AssociatedPair> modelMatcher;
	protected MosaicImagePointKey<I,T> mosaicAlg;
	
	protected RenderImageMosaic<I,?> mosaicRender;

	BufferedImage distortedImage;

	protected StabilizationInfoPanel infoPanel;
	private DisplayPanel gui = new DisplayPanel();
	
	protected int outputWidth;
	protected int outputHeight;

	protected int inputWidth;
	protected int inputHeight;
	
	T fitModel;
	Class<I> imageType;
	boolean colorOutput = true;

	protected int totalKeyFrames = 0;
	protected int totalFatalErrors = 0;

	protected boolean showInput;
	protected boolean showImageView = true;

	public ImageDistortBaseApp( boolean showInput , Class<I> imageType , int numAlgFamilies)
	{
		super(numAlgFamilies);
		this.showInput = showInput;
		this.imageType = imageType;

		infoPanel = new StabilizationInfoPanel();
		infoPanel.setMaximumSize(infoPanel.getPreferredSize());
		gui.addMouseListener(this);

		add(infoPanel, BorderLayout.WEST);
		setMainGUI(gui);
	}

	/**
	 * Specifies the size of the distorted output image.
	 */
	protected void setOutputSize( int width , int height ) {
		this.outputWidth = width;
		this.outputHeight = height;

		mosaicRender = new RenderImageMosaic<I,ImageBase>(outputWidth,outputHeight,imageType,colorOutput);
		distortedImage= new BufferedImage(outputWidth,outputHeight,BufferedImage.TYPE_INT_RGB);

		if(showInput) {
			gui.setPreferredSize(new Dimension(inputWidth+outputBorder+outputWidth, Math.max(inputHeight,outputHeight)));
		} else {
			gui.setPreferredSize(new Dimension(outputWidth, outputHeight));
		}
		gui.setMinimumSize(gui.getPreferredSize());
	}

	@Override
	protected void process(SimpleImageSequence<I> sequence) {
		if( !sequence.hasNext() )
			return;
		stopWorker();

		this.sequence = sequence;
		
		I input = sequence.next();
		inputWidth = input.width;
		inputHeight = input.height;

		doRefreshAll();
	}

	@Override
	protected void updateAlg(I frame, BufferedImage buffImage) {
		if( mosaicAlg == null )
			return;

		if( !mosaicAlg.process(frame) ) {
			totalFatalErrors++;
			handleFatalError();
		}

		renderCurrentTransform(frame, buffImage);
		PixelTransform_F32 pixelTran;

		T keyToCurr = mosaicAlg.getKeyToCurr();
		pixelTran = createPixelTransform(keyToCurr);
		checkStatus(pixelTran,frame,buffImage);
	}

	protected void renderCurrentTransform(I frame, BufferedImage buffImage) {
		T worldToCurr = mosaicAlg.getWorldToCurr();
		PixelTransform_F32 pixelTran = createPixelTransform(worldToCurr);

		mosaicRender.update(frame,buffImage,pixelTran);
	}

	protected abstract void checkStatus( PixelTransform_F32 keyToCurr , I frame , BufferedImage buffImage  );

	protected PixelTransform_F32 createPixelTransform(T worldToCurr) {
		PixelTransform_F32 pixelTran;
		if( worldToCurr instanceof Homography2D_F64) {
			Homography2D_F32 t = UtilHomography.convert((Homography2D_F64)worldToCurr,null);
			pixelTran = new PixelTransformHomography_F32(t);
		} else if( worldToCurr instanceof  Affine2D_F64 ) {
			Affine2D_F32 t = UtilAffine.convert((Affine2D_F64) worldToCurr, null);
			pixelTran = new PixelTransformAffine_F32(t);
		} else {
			throw new RuntimeException("Unknown model type");
		}
		return pixelTran;
	}

	@Override
	protected void updateAlgGUI(I frame, BufferedImage imageGUI, final double fps) {

		// switch between B&W and color mosaic modes
		if( infoPanel.getColor() != mosaicRender.getColorOutput() ) {
			synchronized ( mosaicRender ) {
				mosaicRender.setColorOutput(infoPanel.getColor());
			}
		}

		T worldToCurr = mosaicAlg.getWorldToCurr();
		final T currToWorld = (T)worldToCurr.invert(null);

		ConvertBufferedImage.convertTo(mosaicRender.getMosaic(), distortedImage);

		// toggle on and off the view window
		showImageView = infoPanel.getShowView();
		
		// toggle on and off showing the active tracks
		if( infoPanel.getShowInliers())
			gui.setInliers(modelMatcher.getMatchSet());
		else
			gui.setInliers(null);
		if( infoPanel.getShowAll())
			gui.setAllTracks(tracker.getActiveTracks());
		else
			gui.setAllTracks(null);

		Homography2D_F32 H = convertToHomography(currToWorld);
		gui.setCurrToWorld(H);
		gui.setImages(imageGUI,distortedImage);

		final int numAssociated = modelMatcher.getMatchSet().size();
		final int numFeatures = tracker.getActiveTracks().size();

		// update GUI
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				infoPanel.setFPS(fps);
				infoPanel.setFatalErrors(totalFatalErrors);
				infoPanel.setNumInliers(numAssociated);
				infoPanel.setNumTracks(numFeatures);
				infoPanel.setKeyFrames(totalKeyFrames);
				infoPanel.repaint();

				gui.repaint();
			}
		});
	}

	protected Homography2D_F32 convertToHomography(T m) {

		Homography2D_F32 H = new Homography2D_F32();
		
		if( m instanceof Affine2D_F64) {
			Affine2D_F64 affine = (Affine2D_F64)m;

			H.a11 = (float)affine.a11;
			H.a12 = (float)affine.a12;
			H.a21 = (float)affine.a21;
			H.a22 = (float)affine.a22;
			H.a13 = (float)affine.tx;
			H.a23 = (float)affine.ty;
			H.a31 = 0;
			H.a32 = 0;
			H.a33 = 1;
		} else if( m instanceof Homography2D_F64) {
			Homography2D_F64 h = (Homography2D_F64)m;

			UtilHomography.convert(h, H);

		} else {
			throw new RuntimeException("Unexpected type: "+m.getClass().getSimpleName());
		}
		
		return H;
	}

	protected void drawImageBounds( float scale , int offsetX , int offsetY , int width , int height, Homography2D_F32 currToGlobal, Graphics g2) {
		Point2D_F32 a = new Point2D_F32(0,0);
		Point2D_F32 b = new Point2D_F32(0+width,0);
		Point2D_F32 c = new Point2D_F32(width,height);
		Point2D_F32 d = new Point2D_F32(0,height);

		HomographyPointOps.transform(currToGlobal,a,a);
		HomographyPointOps.transform(currToGlobal,b,b);
		HomographyPointOps.transform(currToGlobal,c,c);
		HomographyPointOps.transform(currToGlobal,d,d);

		a.x = offsetX + a.x*scale; a.y = offsetY + a.y*scale;
		b.x = offsetX + b.x*scale; b.y = offsetY + b.y*scale;
		c.x = offsetX + c.x*scale; c.y = offsetY + c.y*scale;
		d.x = offsetX + d.x*scale; d.y = offsetY + d.y*scale;

		g2.setColor(Color.RED);
		g2.drawLine((int)a.x,(int)a.y,(int)b.x,(int)b.y);
		g2.drawLine((int)b.x,(int)b.y,(int)c.x,(int)c.y);
		g2.drawLine((int)c.x,(int)c.y,(int)d.x,(int)d.y);
		g2.drawLine((int)d.x,(int)d.y,(int)a.x,(int)a.y);
	}

	/**
	 * Draw features after applying a homography transformation.
	 */
	protected void drawFeatures( float scale , int offsetX , int offsetY ,
								 FastQueue<Point2D_F32> all,
								 FastQueue<Point2D_F32> inliers,
								 Homography2D_F32 currToGlobal, Graphics2D g2 ) {

		Point2D_F32 distPt = new Point2D_F32();

		for( int i = 0; i < all.size; i++  ) {
			HomographyPointOps.transform(currToGlobal,all.get(i),distPt);

			distPt.x = offsetX + distPt.x*scale;
			distPt.y = offsetY + distPt.y*scale;

			VisualizeFeatures.drawPoint(g2, (int) distPt.x, (int) distPt.y, Color.RED);
		}

		for( int i = 0; i < inliers.size; i++  ) {
			HomographyPointOps.transform(currToGlobal,inliers.get(i),distPt);

			distPt.x = offsetX + distPt.x*scale;
			distPt.y = offsetY + distPt.y*scale;

			VisualizeFeatures.drawPoint(g2, (int) distPt.x, (int) distPt.y, Color.BLUE);
		}
	}

	/**
	 * Draw features with no extra transformation
	 */
	protected void drawFeatures( float scale , int offsetX , int offsetY ,
								 FastQueue<Point2D_F32> all,
								 FastQueue<Point2D_F32> inliers,
								 Graphics2D g2 ) {

		Point2D_F32 distPt = new Point2D_F32();

		for( int i = 0; i < all.size; i++  ) {
			Point2D_F32 p = all.get(i);

			distPt.x = offsetX + p.x*scale;
			distPt.y = offsetY + p.y*scale;

			VisualizeFeatures.drawPoint(g2, (int) distPt.x, (int) distPt.y, Color.RED);
		}

		for( int i = 0; i < inliers.size; i++  ) {
			Point2D_F32 p = inliers.get(i);

			distPt.x = offsetX + p.x*scale;
			distPt.y = offsetY + p.y*scale;

			VisualizeFeatures.drawPoint(g2, (int) distPt.x, (int) distPt.y, Color.BLUE);
		}
	}

	@Override
	public boolean getHasProcessedImage() {
		return mosaicAlg != null && mosaicAlg.getHasProcessedImage();
	}

	@Override
	public void refreshAll(Object[] cookies) {
		stopWorker();

		tracker = (ImagePointTracker<I>)cookies[0];
		fitModel = (T)cookies[1];

		startEverything();
	}

	@Override
	public void setActiveAlgorithm(int indexFamily, String name, Object cookie) {
		if( sequence == null || modelMatcher == null )
			return;

		stopWorker();

		switch( indexFamily ) {
			case 0:
				tracker = (ImagePointTracker<I>)cookie;
				break;
			
			case 1:
				fitModel = (T)cookie;
				break;
		}

		// restart the video
		sequence.reset();

		startEverything();
	}

	private class DisplayPanel extends JPanel
	{
		BufferedImage orig;
		BufferedImage stabilized;

		FastQueue<Point2D_F32> inliers = new FastQueue<Point2D_F32>(300,Point2D_F32.class,true);
		FastQueue<Point2D_F32> allTracks = new FastQueue<Point2D_F32>(300,Point2D_F32.class,true);

		Homography2D_F32 currToWorld = new Homography2D_F32();

		public void setImages( BufferedImage orig , BufferedImage stabilized )
		{
			this.orig = orig;
			this.stabilized = stabilized;
		}

		public synchronized void setInliers(java.util.List<AssociatedPair> list) {
			inliers.reset();

			if( list != null ) {
				for( AssociatedPair p : list ) {
					inliers.pop().set((float)p.currLoc.x,(float)p.currLoc.y);
				}
			}
		}

		public synchronized void setAllTracks(java.util.List<AssociatedPair> list) {
			allTracks.reset();

			if( list != null ) {
				for( AssociatedPair p : list ) {
					allTracks.pop().set((float)p.currLoc.x,(float)p.currLoc.y);
				}
			}
		}

		@Override
		public synchronized void paintComponent(Graphics g) {
			super.paintComponent(g);

			if( orig == null || stabilized == null )
				return;

			Graphics2D g2 = (Graphics2D)g;

			if (showInput) {
				drawBoth( g2 );
			} else {
				drawJustDistorted( g2 );
			}
		}
		
		private void drawJustDistorted( Graphics2D g2 ) {
			int w = getWidth();
			int h = getHeight();

			double scaleX = w/(double)stabilized.getWidth();
			double scaleY = h/(double)stabilized.getHeight();

			double scale = Math.min(scaleX,scaleY);
			if( scale > 1 ) scale = 1;

			int scaledWidth = (int)(scale*stabilized.getWidth());
			int scaledHeight = (int)(scale*stabilized.getHeight());

			// draw stabilized on right
			g2.drawImage(stabilized,0,0,scaledWidth,scaledHeight,0,0,stabilized.getWidth(),stabilized.getHeight(),null);

			drawFeatures((float)scale,0,0,allTracks,inliers,currToWorld,g2);

			if(showImageView)
				drawImageBounds((float)scale,0,0,orig.getWidth(),orig.getHeight(), currToWorld,g2);
		}
		
		private void drawBoth( Graphics2D g2 ) {
			
			int desiredWidth = orig.getWidth()+stabilized.getWidth();
			int desiredHeight = Math.max(orig.getHeight(),stabilized.getHeight());

			int w = getWidth()-outputBorder;
			int h = getHeight();

			double scaleX = w/(double)desiredWidth;
			double scaleY = h/(double)desiredHeight;

			double scale = Math.min(scaleX,scaleY);
			if( scale > 1 ) scale = 1;

			int scaledInputWidth = (int)(scale*orig.getWidth());
			int scaledInputHeight = (int)(scale*orig.getHeight());

			int scaledOutputWidth = (int)(scale*stabilized.getWidth());
			int scaledOutputHeight = (int)(scale*stabilized.getHeight());

			//  draw undistorted on left
			g2.drawImage(orig,0,0,scaledInputWidth,scaledInputHeight,0,0,orig.getWidth(),orig.getHeight(),null);

			// draw distorted on right
			g2.drawImage(stabilized,scaledInputWidth+outputBorder,0,scaledInputWidth+scaledOutputWidth+outputBorder,scaledOutputHeight,0,0,outputWidth,outputHeight,null);

			drawFeatures((float)scale,0,0,allTracks,inliers,g2);
			drawFeatures((float)scale,scaledInputWidth+outputBorder,0,allTracks,inliers,currToWorld,g2);

			if(showImageView)
				drawImageBounds((float)scale,scaledInputWidth+outputBorder,0,orig.getWidth(),orig.getHeight(), currToWorld,g2);
		}

		public void setCurrToWorld(Homography2D_F32 currToWorld) {
			this.currToWorld.set(currToWorld);
		}
	}

	protected void createModelMatcher( int maxIterations , double thresholdFit ) {

		ModelFitter fitter;
		DistanceFromModel distance;
		
		if( fitModel instanceof Homography2D_F64 ) {
			fitter = new ModelFitterLinearHomography();
			distance = new DistanceHomographySq();
		} else if( fitModel instanceof Affine2D_F64 ) {
			fitter = new ModelFitterAffine2D();
			distance = new DistanceAffine2DSq();
		} else {
			throw new RuntimeException("Unknown model type");
		}

		int numSample =  fitter.getMinimumPoints();
		modelMatcher = new SimpleInlierRansac(123123,
				fitter,distance,maxIterations,numSample,numSample,10000,thresholdFit);
	}

	protected abstract void startEverything();
	
	protected abstract void handleFatalError();
}