/****************************************************************************
Copyright (c) 2007, Colorado School of Mines and others. All rights reserved.
This program and accompanying materials are made available under the terms of
the Common Public License - v1.0, which accompanies this distribution, and is 
available at http://www.eclipse.org/legal/cpl-v10.html
****************************************************************************/
package ldf;

import edu.mines.jtk.dsp.*;
import edu.mines.jtk.util.*;
import static edu.mines.jtk.util.MathPlus.*;

/**
 * Local anisotropic smoothing filter.
 * @author Dave Hale, Colorado School of Mines
 * @version 2007.09.21
 */
public class LocalSmoothingFilter {

  /**
   * Constructs a local smoothing filter.
   * @param sigma the filter half-width.
   */
  public LocalSmoothingFilter(double sigma) {
    _sigma = (float)sigma;
  }

  /**
   * Constructs a local smoothing filter.
   * @param sigma the filter half-width.
   */
  public LocalSmoothingFilter(double sigma, double aniso) {
    _sigma = (float)sigma;
    _aniso = (float)aniso;
  }

  public void applyPass(float[] es, float[] x, float[] y) {
    apply1(es,x,y);
  }

  public void applyKill(float[] es, float[] x, float[] y) {
    applyPass(es,x,y);
    Array.sub(x,y,y);
  }

  /**
   * Applies a filter that enhances (passes) features that are locally 
   * linear with inline vectors v.
   * @param ds anisotropic diffusivity scale factors; null, for no scaling.
   * @param es isotropic diffusivity scale factors; null, for no scaling.
   * @param v1 array of 1st components of inline unit vectors.
   * @param x array with input image; must be distinct from y.
   * @param y array with output image; must be distinct from x.
   */
  public void applyPass(
    float[][] ds, float[][] es, float[][] v1, 
    float[][] x, float[][] y) 
  {
    int n1 = x[0].length;
    int n2 = x.length;
    float[][] t = new float[n2][n1];
    apply1(es,x,y);
    apply2(es,y,t);
    apply12(ds,v1,t,y);
  }
  public void applyPassTranspose(
    float[][] ds, float[][] es, float[][] v1, 
    float[][] x, float[][] y) 
  {
    int n1 = x[0].length;
    int n2 = x.length;
    float[][] t = new float[n2][n1];
    apply12(ds,v1,x,y);
    apply2(es,y,t);
    apply1(es,t,y);
  }

  /**
   * Applies a filter that attenuates (kills) features that are locally 
   * linear with inline vectors v.
   * @param ds diffusivity scale factors; null, for no scaling.
   * @param v1 array of 1st components of inline unit vectors.
   * @param x array with input image; must be distinct from y.
   * @param y array with output image; must be distinct from x.
   */
  public void applyKill(
    float[][] ds, float[][] es, float[][] v1, 
    float[][] x, float[][] y) 
  {
    applyPass(ds,es,v1,x,y);
    Array.sub(x,y,y);
  }

  /**
   * Encodes specified fractions as 8-bit byte percentages.
   * Fractions are clipped to lie in the range [0,1].
   * @param s array of fractions.
   * @return array of 8-bit (byte) percentages.
   */
  public static byte[] encodeFractions(float[] s) {
    int n = s.length;
    byte[] b = new byte[n];
    for (int i=0; i<n; ++i) {
      float si = s[i];
      if (si<0.0f) {
        b[i] = 0;
      } else if (si>1.0f) {
        b[i] = 100;
      } else {
        b[i] = (byte)(si*100+0.5f);
      }
    }
    return b;
  }

  /**
   * Encodes specified fractions as 8-bit byte percentages.
   * Fractions are clipped to lie in the range [0,1].
   * @param s array of fractions.
   * @return array of 8-bit (byte) percentages.
   */
  public static byte[][] encodeFractions(float[][] s) {
    int n = s.length;
    byte[][] b = new byte[n][];
    for (int i=0; i<n; ++i) {
      b[i] = encodeFractions(s[i]);
    }
    return b;
  }

  /**
   * Encodes specified fractions as 8-bit byte percentages.
   * Fractions are clipped to lie in the range [0,1].
   * @param s array of fractions.
   * @return array of 8-bit (byte) percentages.
   */
  public static byte[][][] encodeFractions(float[][][] s) {
    int n = s.length;
    byte[][][] b = new byte[n][][];
    for (int i=0; i<n; ++i) {
      b[i] = encodeFractions(s[i]);
    }
    return b;
  }

  /**
   * Encodes specified unit vectors as 16-bit (short) indices.
   * @param u1 array of u1-components of unit vectors.
   * @param u2 array of u2-components of unit vectors.
   * @param u3 array of u3-components of unit vectors.
   * @return array of 16-bit (short) indices.
   */
  public static short[] encodeUnitVectors(float[] u1, float[] u2, float[] u3) {
    return UnitSphereSampling.encode16(u3,u2,u1);
  }

  /**
   * Encodes specified unit vectors as 16-bit (short) indices.
   * @param u1 array of u1-components of unit vectors.
   * @param u2 array of u2-components of unit vectors.
   * @param u3 array of u3-components of unit vectors.
   * @return array of 16-bit (short) indices.
   */
  public static short[][] encodeUnitVectors(
    float[][] u1, float[][] u2, float[][] u3) 
  {
    return UnitSphereSampling.encode16(u3,u2,u1);
  }

  /**
   * Encodes specified unit vectors as 16-bit (short) indices.
   * @param u1 array of u1-components of unit vectors.
   * @param u2 array of u2-components of unit vectors.
   * @param u3 array of u3-components of unit vectors.
   * @return array of 16-bit (short) indices.
   */
  public static short[][][] encodeUnitVectors(
    float[][][] u1, float[][][] u2, float[][][] u3) 
  {
    return UnitSphereSampling.encode16(u3,u2,u1);
  }

  ///////////////////////////////////////////////////////////////////////////
  // private

  private float _sigma; // filter half-width
  private float _aniso; // anisotropy

  private static void maket(float[] v1, float[] u1, float[] t1) {
    int n1 = v1.length;
    float t1min = 0.0f;
    float t1max = (float)(n1-1);
    for (int i1=0; i1<n1; ++i1) {
      float v1i = v1[i1];
      float v2i = sqrt(1.0f-v1i*v1i);
      float vi = v1i/v2i;
      t1[i1] = max(t1min,min(t1max,u1[i1]-vi));
    }
  }

  private void apply1(float[] es, float[] x, float[] y) {
    int n1 = x.length;

    // Sub-diagonal of SPD tridiagonal matrix A in array e.
    float[] e = new float[n1];
    float ss = 0.50f*_sigma*_sigma;
    for (int i1=1; i1<n1; ++i1)
      e[i1] = -ss;
    if (es!=null) {
      for (int i1=1; i1<n1; ++i1)
        e[i1] *= 0.50f*(es[i1-1]+es[i1]);
    }

    // Diagonal of SPD tridiagonal matrix A in array d.
    float[] d = new float[n1];
    d[0] = 1.0f-e[1];
    for (int i1=1; i1<n1-1; ++i1) {
      d[i1] = 1.0f-e[i1]-e[i1+1];
    }
    d[n1-1] = 1.0f-e[n1-1];

    // A = L*inv(D)*L', where L is lower unit bidiagonal, and D is diagonal.
    // Lower sub-diagonal of L goes in array e; diagonal of D in array d.
    d[0] = 1.0f/d[0];
    for (int i1=1; i1<n1; ++i1) {
      float t = e[i1];
      e[i1] = t*d[i1-1];
      d[i1] = 1.0f/(d[i1]-t*e[i1]);
    }

    // y = inv(L) * x.
    y[0] = x[0];
    for (int i1=1; i1<n1; ++i1) {
      y[i1] = x[i1]-e[i1]*y[i1-1];
    }

    // y = D * inv(L) * x.
    for (int i1=0; i1<n1; ++i1) {
      y[i1] *= d[i1];
    }

    // y = inv(L') * D * inv(L) * x.
    for (int i1=n1-1; i1>0; --i1) {
      y[i1-1] -= e[i1]*y[i1];
    }
  }

  private void apply1( float[][] es, float[][] x, float[][] y) {
    int n2 = x.length;
    for (int i2=0; i2<n2; ++i2) {
      float[] esi2 = (es!=null)?es[i2]:null;
      apply1(esi2,x[i2],y[i2]);
    }
  }

  private void apply2( float[][] es, float[][] x, float[][] y) {
    int n1 = x[0].length;
    int n2 = x.length;

    // Sub-diagonal of SPD tridiagonal matrix A in array e.
    float[][] e = new float[n2][n1];
    float ss = 0.50f*_sigma*_sigma;
    for (int i2=1; i2<n2; ++i2)
      for (int i1=0; i1<n1; ++i1)
        e[i2][i1] = -ss;
    if (es!=null) {
      for (int i2=n2-1; i2>0; --i2)
        for (int i1=0; i1<n1; ++i1)
          e[i2][i1] *= 0.5f*(es[i2-1][i1]+es[i2][i1]);
    }

    // Diagonal of SPD tridiagonal matrix A in array d.
    float[][] d = new float[n2][n1];
    for (int i1=0; i1<n1; ++i1)
      d[0][i1] = 1.0f-e[1][i1];
    for (int i2=1; i2<n2-1; ++i2)
      for (int i1=0; i1<n1; ++i1)
        d[i2][i1] = 1.0f-e[i2][i1]-e[i2+1][i1];
    for (int i1=0; i1<n1; ++i1)
      d[n2-1][i1] = 1.0f-e[n2-1][i1];

    // A = L*inv(D)*L', where L is lower unit bidiagonal, and D is diagonal.
    // Lower sub-diagonal of L goes in array e; diagonal of D in array d.
    for (int i1=0; i1<n1; ++i1)
      d[0][i1] = 1.0f/d[0][i1];
    for (int i2=1; i2<n2; ++i2) {
      for (int i1=0; i1<n1; ++i1) {
        float t = e[i2][i1];
        e[i2][i1] = t*d[i2-1][i1];
        d[i2][i1] = 1.0f/(d[i2][i1]-t*e[i2][i1]);
      }
    }

    // y = inv(L) * x.
    for (int i1=0; i1<n1; ++i1)
      y[0][i1] = x[0][i1];
    for (int i2=1; i2<n2; ++i2) {
      for (int i1=0; i1<n1; ++i1)
        y[i2][i1] = x[i2][i1]-e[i2][i1]*y[i2-1][i1];
    }

    // y = D * inv(L) * x.
    for (int i2=0; i2<n2; ++i2)
      for (int i1=0; i1<n1; ++i1)
        y[i2][i1] *= d[i2][i1];

    // y = inv(L') * D * inv(L) * x.
    for (int i2=n2-1; i2>0; --i2) {
      for (int i1=0; i1<n1; ++i1)
        y[i2-1][i1] -= e[i2][i1]*y[i2][i1];
    }
  }

  private void apply12( float[][] ds, float[][] v1, float[][] x, float[][] y) {
    int n1 = x[0].length;
    int n2 = x.length;

    // Sinc interpolator.
    SincInterpolator si = new SincInterpolator();
    //si.setExtrapolation(SincInterpolator.Extrapolation.CONSTANT);
    si.setUniformSampling(n1,1.0f,0.0f);
    float[] t1 = new float[n1];
    float[] y1 = new float[n1];
    float[] u1 = Array.rampfloat(0.0f,1.0f,n1);

    // Sub-diagonal of SPD tridiagonal matrix A in array e.
    float[][] e = new float[n2][n1];
    float sigma = _sigma*_aniso;
    float ss = 0.50f*sigma*sigma;
    for (int i2=1; i2<n2; ++i2) {
      for (int i1=0; i1<n1; ++i1) {
        float v1i = v1[i2][i1];
        float v2s = 1.0f-v1i*v1i;
        e[i2][i1] = -ss*v2s;
        if (ds!=null) 
          e[i2][i1] *= ds[i2][i1];
      }
    }
    for (int i2=n2-1; i2>0; --i2)
      for (int i1=0; i1<n1; ++i1)
        e[i2][i1] = 0.5f*(e[i2-1][i1]+e[i2][i1]);

    // Diagonal of SPD tridiagonal matrix A in array d.
    float[][] d = new float[n2][n1];
    for (int i1=0; i1<n1; ++i1)
      d[0][i1] = 1.0f-e[1][i1];
    for (int i2=1; i2<n2-1; ++i2)
      for (int i1=0; i1<n1; ++i1)
        d[i2][i1] = 1.0f-e[i2][i1]-e[i2+1][i1];
    for (int i1=0; i1<n1; ++i1)
      d[n2-1][i1] = 1.0f-e[n2-1][i1];

    // A = L*inv(D)*L', where L is lower unit bidiagonal, and D is diagonal.
    // Lower sub-diagonal of L goes in array e; diagonal of D in array d.
    for (int i1=0; i1<n1; ++i1)
      d[0][i1] = 1.0f/d[0][i1];
    for (int i2=1; i2<n2; ++i2) {
      for (int i1=0; i1<n1; ++i1) {
        float t = e[i2][i1];
        e[i2][i1] = t*d[i2-1][i1];
        d[i2][i1] = 1.0f/(d[i2][i1]-t*e[i2][i1]);
      }
    }

    // y = inv(L) * x.
    for (int i1=0; i1<n1; ++i1)
      y[0][i1] = x[0][i1];
    for (int i2=1; i2<n2; ++i2) {
      maket(v1[i2],u1,t1);
      si.setUniformSamples(y[i2-1]);
      si.interpolate(n1,t1,y1);
      for (int i1=0; i1<n1; ++i1)
        y[i2][i1] = x[i2][i1]-e[i2][i1]*y1[i1];
    }

    // y = D * inv(L) * x.
    for (int i2=0; i2<n2; ++i2)
      for (int i1=0; i1<n1; ++i1)
        y[i2][i1] *= d[i2][i1];

    // y = inv(L') * D * inv(L) * x.
    for (int i2=n2-1; i2>0; --i2) {
      for (int i1=0; i1<n1; ++i1)
        y1[i1] = -e[i2][i1]*y[i2][i1];
      maket(v1[i2],u1,t1);
      si.setUniformSamples(y[i2-1]);
      si.accumulate(n1,t1,y1);
    }
  }
}
