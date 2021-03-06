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

package boofcv.alg.enhance.impl;

import boofcv.misc.AutoTypeImage;
import boofcv.misc.CodeGeneratorBase;

import java.io.FileNotFoundException;

/**
 * @author Peter Abeles
 */
public class GenerateImplEnhanceFilter extends CodeGeneratorBase {
	String className = "ImplEnhanceFilter";

	public GenerateImplEnhanceFilter() throws FileNotFoundException {
		setOutputFile(className);
	}

	@Override
	public void generate() throws FileNotFoundException {
		printPreamble();

		sharpen4(AutoTypeImage.U8);
		sharpenBorder4(AutoTypeImage.U8);
		sharpen4(AutoTypeImage.F32);
		sharpenBorder4(AutoTypeImage.F32);
		sharpen8(AutoTypeImage.U8);
		sharpenBorder8(AutoTypeImage.U8);
		sharpen8(AutoTypeImage.F32);
		sharpenBorder8(AutoTypeImage.F32);

		printVarious();

		out.print("\n" +
				"}\n");
	}

	private void printPreamble() {
		out.print("import boofcv.struct.convolve.Kernel2D_F32;\n" +
				"import boofcv.struct.convolve.Kernel2D_I32;\n" +
				"import boofcv.struct.image.*;\n" +
				"\n" +
				"/**\n" +
				" * <p>\n" +
				" * Filter based functions for image enhancement.\n" +
				" * </p>\n" +
				" *\n" +
				" * <p>\n" +
				" * NOTE: Do not modify.  Automatically generated by {@link GenerateImplEnhanceFilter}.\n" +
				" * </p>\n" +
				" *\n" +
				" * @author Peter Abeles\n" +
				" */\n" +
				"public class ImplEnhanceFilter {\n" +
				"\n" +
				"\tpublic static Kernel2D_I32 kernelEnhance4_I32 = new Kernel2D_I32(3, new int[]{0,-1,0,-1,5,-1,0,-1,0});\n" +
				"\tpublic static Kernel2D_F32 kernelEnhance4_F32 = new Kernel2D_F32(3, new float[]{0,-1,0,-1,5,-1,0,-1,0});\n" +
				"\tpublic static Kernel2D_I32 kernelEnhance8_I32 = new Kernel2D_I32(3, new int[]{-1,-1,-1,-1,9,-1,-1,-1,-1});\n" +
				"\tpublic static Kernel2D_F32 kernelEnhance8_F32 = new Kernel2D_F32(3, new float[]{-1,-1,-1,-1,9,-1,-1,-1,-1});\n\n");
	}

	private void sharpen4(AutoTypeImage image) {
		String name = image.getSingleBandName();
		String bitwise = image.getBitWise();
		String cast = image.getTypeCastFromSum();
		String sumtype = image.getSumType();

		out.print("\tpublic static void sharpenInner4( "+name+" input , "+name+" output , "+sumtype+" minValue , "+sumtype+" maxValue ) {\n" +
				"\t\tfor( int y = 1; y < input.height-1; y++ ) {\n" +
				"\t\t\tint indexIn = input.startIndex + y*input.stride + 1;\n" +
				"\t\t\tint indexOut = output.startIndex + y*output.stride + 1;\n" +
				"\n" +
				"\t\t\tfor( int x = 1; x < input.width-1; x++ , indexIn++,indexOut++) {\n" +
				"\n" +
				"\t\t\t\t"+sumtype+" a = 5*(input.data[indexIn] "+bitwise+") - (\n" +
				"\t\t\t\t\t\t(input.data[indexIn-1] "+bitwise+")+(input.data[indexIn+1] "+bitwise+") +\n" +
				"\t\t\t\t\t\t\t\t(input.data[indexIn-input.stride] "+bitwise+") + (input.data[indexIn+input.stride] "+bitwise+"));\n" +
				"\n" +
				"\t\t\t\tif( a > maxValue )\n" +
				"\t\t\t\t\ta = maxValue;\n" +
				"\t\t\t\telse if( a < minValue )\n" +
				"\t\t\t\t\ta = minValue;\n" +
				"\n" +
				"\t\t\t\toutput.data[indexOut] = "+cast+"a;\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\t}\n\n");
	}

	private void sharpenBorder4( AutoTypeImage image ) {

		String name = image.getSingleBandName();
		String cast = image.getTypeCastFromSum();
		String sumtype = image.getSumType();

		out.print("\tpublic static void sharpenBorder4( "+name+" input , "+name+" output , "+sumtype+" minValue , "+sumtype+" maxValue ) {\n" +
				"\t\t"+sumtype+" value;\n" +
				"\n" +
				"\t\tint b = input.height-1;\n" +
				"\n" +
				"\t\tint indexTop = input.startIndex;\n" +
				"\t\tint indexBottom = input.startIndex + b*input.stride;\n" +
				"\t\t\n" +
				"\t\tfor( int x = 0; x < input.width; x++ ) {\n" +
				"\t\t\tvalue = 4*safeGet(input,x,0) - (safeGet(input,x-1,0) + safeGet(input,x+1,0) + safeGet(input,x,1));\n" +
				"\n" +
				"\t\t\tif( value > maxValue )\n" +
				"\t\t\t\tvalue = maxValue;\n" +
				"\t\t\telse if( value < minValue )\n" +
				"\t\t\t\tvalue = minValue;\n" +
				"\n" +
				"\t\t\toutput.data[indexTop++] = "+cast+"value;\n" +
				"\n" +
				"\t\t\tvalue = 4*safeGet(input,x,b) - (safeGet(input,x-1,b) + safeGet(input,x+1,b) + safeGet(input,x,b-1));\n" +
				"\n" +
				"\t\t\tif( value > maxValue )\n" +
				"\t\t\t\tvalue = maxValue;\n" +
				"\t\t\telse if( value < minValue )\n" +
				"\t\t\t\tvalue = minValue;\n" +
				"\n" +
				"\t\t\toutput.data[indexBottom++] = "+cast+"value;\n" +
				"\t\t}\n" +
				"\n" +
				"\t\tb = input.width-1;\n" +
				"\t\tint indexLeft = input.startIndex + input.stride;\n" +
				"\t\tint indexRight = input.startIndex + input.stride + b;\n" +
				"\n" +
				"\t\tfor( int y = 1; y < input.height-1; y++ ) {\n" +
				"\t\t\tvalue = 4*safeGet(input,0,y) - (safeGet(input,1,y) + safeGet(input,0,y-1) + safeGet(input,0,y+1));\n" +
				"\n" +
				"\t\t\tif( value > maxValue )\n" +
				"\t\t\t\tvalue = maxValue;\n" +
				"\t\t\telse if( value < minValue )\n" +
				"\t\t\t\tvalue = minValue;\n" +
				"\n" +
				"\t\t\toutput.data[indexLeft] = "+cast+"value;\n" +
				"\n" +
				"\t\t\tvalue = 4*safeGet(input,b,y) - (safeGet(input,b-1,y) + safeGet(input,b,y-1) + safeGet(input,b,y+1));\n" +
				"\n" +
				"\t\t\tif( value > maxValue )\n" +
				"\t\t\t\tvalue = maxValue;\n" +
				"\t\t\telse if( value < minValue )\n" +
				"\t\t\t\tvalue = minValue;\n" +
				"\n" +
				"\t\t\toutput.data[indexRight] = "+cast+"value;\n" +
				"\t\t\t\n" +
				"\t\t\tindexLeft += input.stride;\n" +
				"\t\t\tindexRight += input.stride;\n" +
				"\t\t}\n" +
				"\t}\n\n");
	}

	private void sharpen8(AutoTypeImage image) {
		String name = image.getSingleBandName();
		String bitwise = image.getBitWise();
		String cast = image.getTypeCastFromSum();
		String sumtype = image.getSumType();

		out.print("\tpublic static void sharpenInner8( "+name+" input , "+name+" output , "+sumtype+" minValue , "+sumtype+" maxValue ) {\n" +
				"\t\tfor( int y = 1; y < input.height-1; y++ ) {\n" +
				"\t\t\tint indexIn = input.startIndex + y*input.stride + 1;\n" +
				"\t\t\tint indexOut = output.startIndex + y*output.stride + 1;\n" +
				"\n" +
				"\t\t\tfor( int x = 1; x < input.width-1; x++ , indexIn++,indexOut++) {\n" +
				"\n" +
				"\t\t\t\t"+sumtype+" a11 = input.data[indexIn-input.stride-1] "+bitwise+";\n" +
				"\t\t\t\t"+sumtype+" a12 = input.data[indexIn-input.stride] "+bitwise+";\n" +
				"\t\t\t\t"+sumtype+" a13 = input.data[indexIn-input.stride+1] "+bitwise+";\n" +
				"\t\t\t\t"+sumtype+" a21 = input.data[indexIn-1] "+bitwise+";\n" +
				"\t\t\t\t"+sumtype+" a22 = input.data[indexIn] "+bitwise+";\n" +
				"\t\t\t\t"+sumtype+" a23 = input.data[indexIn+1] "+bitwise+";\n" +
				"\t\t\t\t"+sumtype+" a31 = input.data[indexIn+input.stride-1] "+bitwise+";\n" +
				"\t\t\t\t"+sumtype+" a32 = input.data[indexIn+input.stride] "+bitwise+";\n" +
				"\t\t\t\t"+sumtype+" a33 = input.data[indexIn+input.stride+1] "+bitwise+";\n" +
				"\t\t\t\t\n" +
				"\t\t\t\t"+sumtype+" result = 9*a22 - (a11+a12+a13+a21+a23+a31+a32+a33);\n" +
				"\n" +
				"\t\t\t\tif( result > maxValue )\n" +
				"\t\t\t\t\tresult = maxValue;\n" +
				"\t\t\t\telse if( result < minValue )\n" +
				"\t\t\t\t\tresult = minValue;\n" +
				"\n" +
				"\t\t\t\toutput.data[indexOut] = "+cast+"result;\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\t}\n\n");
	}

	private void sharpenBorder8(AutoTypeImage image) {
		String name = image.getSingleBandName();
		String cast = image.getTypeCastFromSum();
		String sumtype = image.getSumType();

		out.print("\tpublic static void sharpenBorder8( "+name+" input , "+name+" output , "+sumtype+" minValue , "+sumtype+" maxValue ) {\n" +
				"\t\t"+sumtype+" value;\n" +
				"\n" +
				"\t\tint b = input.height-1;\n" +
				"\t\t"+sumtype+" a11,a12,a13,a21,a22,a23,a31,a32,a33;\n" +
				"\n" +
				"\t\tint indexTop = input.startIndex;\n" +
				"\t\tint indexBottom = input.startIndex + b*input.stride;\n" +
				"\n" +
				"\t\tfor( int x = 0; x < input.width; x++ ) {\n" +
				"\n" +
				"\t\t\ta11 = safeGet(input,x-1,-1);\n" +
				"\t\t\ta12 = safeGet(input,x  ,-1);\n" +
				"\t\t\ta13 = safeGet(input,x+1,-1);\n" +
				"\t\t\ta21 = safeGet(input,x-1, 0);\n" +
				"\t\t\ta22 = safeGet(input,x  , 0);\n" +
				"\t\t\ta23 = safeGet(input,x+1, 0);\n" +
				"\t\t\ta31 = safeGet(input,x-1, 1);\n" +
				"\t\t\ta32 = safeGet(input,x  , 1);\n" +
				"\t\t\ta33 = safeGet(input,x+1, 1);\n" +
				"\n" +
				"\t\t\tvalue = 9*a22 - (a11+a12+a13+a21+a23+a31+a32+a33);\n" +
				"\n" +
				"\t\t\tif( value > maxValue )\n" +
				"\t\t\t\tvalue = maxValue;\n" +
				"\t\t\telse if( value < minValue )\n" +
				"\t\t\t\tvalue = minValue;\n" +
				"\n" +
				"\t\t\toutput.data[indexTop++] = "+cast+"value;\n" +
				"\n" +
				"\t\t\ta11 = safeGet(input,x-1,b-1);\n" +
				"\t\t\ta12 = safeGet(input,x  ,b-1);\n" +
				"\t\t\ta13 = safeGet(input,x+1,b-1);\n" +
				"\t\t\ta21 = safeGet(input,x-1, b);\n" +
				"\t\t\ta22 = safeGet(input,x  , b);\n" +
				"\t\t\ta23 = safeGet(input,x+1, b);\n" +
				"\t\t\ta31 = safeGet(input,x-1,b+1);\n" +
				"\t\t\ta32 = safeGet(input,x  ,b+1);\n" +
				"\t\t\ta33 = safeGet(input,x+1,b+1);\n" +
				"\n" +
				"\t\t\tvalue = 9*a22 - (a11+a12+a13+a21+a23+a31+a32+a33);\n" +
				"\n" +
				"\t\t\tif( value > maxValue )\n" +
				"\t\t\t\tvalue = maxValue;\n" +
				"\t\t\telse if( value < minValue )\n" +
				"\t\t\t\tvalue = minValue;\n" +
				"\n" +
				"\t\t\toutput.data[indexBottom++] = "+cast+"value;\n" +
				"\t\t}\n" +
				"\n" +
				"\t\tb = input.width-1;\n" +
				"\t\tint indexLeft = input.startIndex + input.stride;\n" +
				"\t\tint indexRight = input.startIndex + input.stride + b;\n" +
				"\n" +
				"\t\tfor( int y = 1; y < input.height-1; y++ ) {\n" +
				"\t\t\ta11 = safeGet(input,-1,y-1);\n" +
				"\t\t\ta12 = safeGet(input, 0,y-1);\n" +
				"\t\t\ta13 = safeGet(input,+1,y-1);\n" +
				"\t\t\ta21 = safeGet(input,-1, y );\n" +
				"\t\t\ta22 = safeGet(input, 0, y );\n" +
				"\t\t\ta23 = safeGet(input,+1, y );\n" +
				"\t\t\ta31 = safeGet(input,-1,y+1);\n" +
				"\t\t\ta32 = safeGet(input, 0,y+1);\n" +
				"\t\t\ta33 = safeGet(input,+1,y+1);\n" +
				"\n" +
				"\t\t\tvalue = 9*a22 - (a11+a12+a13+a21+a23+a31+a32+a33);\n" +
				"\n" +
				"\t\t\tif( value > maxValue )\n" +
				"\t\t\t\tvalue = maxValue;\n" +
				"\t\t\telse if( value < minValue )\n" +
				"\t\t\t\tvalue = minValue;\n" +
				"\n" +
				"\t\t\toutput.data[indexLeft] = "+cast+"value;\n" +
				"\n" +
				"\t\t\ta11 = safeGet(input,b-1,y-1);\n" +
				"\t\t\ta12 = safeGet(input, b ,y-1);\n" +
				"\t\t\ta13 = safeGet(input,b+1,y-1);\n" +
				"\t\t\ta21 = safeGet(input,b-1, y );\n" +
				"\t\t\ta22 = safeGet(input, b , y );\n" +
				"\t\t\ta23 = safeGet(input,b+1, y );\n" +
				"\t\t\ta31 = safeGet(input,b-1,y+1);\n" +
				"\t\t\ta32 = safeGet(input, b ,y+1);\n" +
				"\t\t\ta33 = safeGet(input,b+1,y+1);\n" +
				"\n" +
				"\t\t\tvalue = 9*a22 - (a11+a12+a13+a21+a23+a31+a32+a33);\n" +
				"\n" +
				"\t\t\tif( value > maxValue )\n" +
				"\t\t\t\tvalue = maxValue;\n" +
				"\t\t\telse if( value < minValue )\n" +
				"\t\t\t\tvalue = minValue;\n" +
				"\n" +
				"\t\t\toutput.data[indexRight] = "+cast+"value;\n" +
				"\n" +
				"\t\t\tindexLeft += input.stride;\n" +
				"\t\t\tindexRight += input.stride;\n" +
				"\t\t}\n" +
				"\t}\n\n");
	}

	private void printVarious() {
		out.print("\t/**\n" +
				"\t * Handle outside image pixels by extending the image.\n" +
				"\t */\n" +
				"\tpublic static int safeGet( ImageInteger input , int x , int y ) {\n" +
				"\t\tif( x < 0 )\n" +
				"\t\t\tx = 0;\n" +
				"\t\telse if( x >= input.width )\n" +
				"\t\t\tx = input.width-1;\n" +
				"\t\tif( y < 0 )\n" +
				"\t\t\ty = 0;\n" +
				"\t\telse if( y >= input.height )\n" +
				"\t\t\ty = input.height-1;\n" +
				"\n" +
				"\t\treturn input.unsafe_get(x,y);\n" +
				"\t}\n" +
				"\n" +
				"\t/**\n" +
				"\t * Handle outside image pixels by extending the image.\n" +
				"\t */\n" +
				"\tpublic static float safeGet( GrayF32 input , int x , int y ) {\n" +
				"\t\tif( x < 0 )\n" +
				"\t\t\tx = 0;\n" +
				"\t\telse if( x >= input.width )\n" +
				"\t\t\tx = input.width-1;\n" +
				"\t\tif( y < 0 )\n" +
				"\t\t\ty = 0;\n" +
				"\t\telse if( y >= input.height )\n" +
				"\t\t\ty = input.height-1;\n" +
				"\n" +
				"\t\treturn input.unsafe_get(x,y);\n" +
				"\t}\n\n");
	}

	public static void main( String args[] ) throws FileNotFoundException {
		GenerateImplEnhanceFilter app = new GenerateImplEnhanceFilter();
		app.generate();
	}
}
