/****************************************************************************
Copyright (c) 2014, Colorado School of Mines and others. All rights reserved.
This program and accompanying materials are made available under the terms of
the Common Public License - v1.0, which accompanies this distribution, and is 
available at http://www.eclipse.org/legal/cpl-v10.html
****************************************************************************/
package fah;

import java.util.*;

import edu.mines.jtk.awt.*;
import edu.mines.jtk.dsp.*;
import edu.mines.jtk.util.*;

import static edu.mines.jtk.util.ArrayMath.*;
import static fah.FaultGeometry.*;

/**
 * Uses image samples alongside fault skins to estimate fault dip slips.
 *
 * @author Dave Hale, Colorado School of Mines
 * @version 2014.07.05
 */
public class FaultSlipper {

  /**
   * Computes fault shifts for all cells in the specified skin.
   */
  public void computeShifts(
      FaultSkin skin, double smax, 
      float[][][] f, float[][][] p2, float[][][] p3) {
    int lmax = (int)abs(smax); // DynamicWarping needs an integer limit
    float d = 2.0f; // use image values two samples away from fault
    FaultCell[][] cab = skin.getCellsAB();
    FaultCell[][] clr = skin.getCellsLR();
    computeErrorsAndInitShifts(skin,lmax,d,f,p2,p3);
    extrapolateErrors(cab);
    DynamicWarping dw = new DynamicWarping(-lmax,lmax);
    dw.setStrainMax(0.25,0.25);
    findShiftsMP(dw,cab,clr);
    findShiftsPM(dw,cab,clr);
    filterShifts(skin);
    smoothShifts(skin);
    smoothShifts(skin);
  }

  ///////////////////////////////////////////////////////////////////////////
  // private

  /**
   * Computes alignment errors and initializes shifts for specified skin.
   */
  private static void computeErrorsAndInitShifts(
      FaultSkin skin, int lmax, float d, 
      float[][][] f, float[][][] p2, float[][][] p3) {
    for (FaultCell cell:skin)
      computeErrorsAndInitShifts(cell,lmax,d,f,p2,p3);
  }

  /**
   * Computes alignment errors and initializes shifts for one cell. Computes
   * both minus-plus (emp) and plus-minus (epm) errors. The minus-plus errors
   * correspond to differences between the sample value on the minus side of
   * the cell and those for the plus sides of cells up and down dip from the
   * cell. The plus-minus errors are similarly defined for opposite sides.
   * <p> 
   * This method uses specified slopes to initialize both minus-plus and
   * plus-minus shifts to compensate for the fact that shifts are estimated
   * using image samples located a horizontal distance d away from this cell. 
   * <p> 
   * For lags where image sample values are unavailable, say, near surface
   * boundaries, errors are extrapolated from other lags, but are negated, so
   * that extrapolated errors can be detected and modified later, after errors
   * for all relevant cells have been computed.
   */
  private static void computeErrorsAndInitShifts(
      FaultCell cell, int lmax, float d, 
      float[][][] f, float[][][] p2, float[][][] p3) {
    int n1 = f[0][0].length;
    float[] y = new float[3];

    // New arrays for alignment errors.
    float[] emp = cell.emp = new float[lmax+1+lmax];
    float[] epm = cell.epm = new float[lmax+1+lmax];

    // Errors for lag zero.
    float d2 =  d*cell.v3;
    float d3 = -d*cell.v2;
    float y1 = cell.x1, y2 = cell.x2, y3 = cell.x3;
    float fm = imageValueAt(y1,y2-d2,y3-d3,f);
    float fp = imageValueAt(y1,y2+d2,y3+d3,f);
    float gm = fm;
    float gp = fp;
    float p2m = imageValueAt(y1,y2-d2,y3-d3,p2);
    float p2p = imageValueAt(y1,y2+d2,y3+d3,p2);
    float p3m = imageValueAt(y1,y2-d2,y3-d3,p3);
    float p3p = imageValueAt(y1,y2+d2,y3+d3,p3);
    float empl = emp[lmax] = alignmentError(fm,gp);
    float epml = epm[lmax] = alignmentError(fp,gm);

    // Initial shifts compensate for horizontal distance d.
    float s23 = d*((p2m+p2p)*cell.w2+(p3m+p3p)*cell.w3);
    cell.smp = -s23;
    cell.spm =  s23;

    // Errors for samples above; make any extrapolated errors negative.
    FaultCell ca = cell;
    int nlaga = min(lmax,ca.i1);
    y1 = cell.x1; y2 = cell.x2; y3 = cell.x3;
    for (int ilag=1; ilag<=lmax; ++ilag) {
      if (ilag<=nlaga) {
        y[0] = y1; y[1] = y2; y[2] = y3;
        ca = ca.walkUpDipFrom(y);
        y1 = y[0]; y2 = y[1]; y3 = y[2];
        d2 =  d*ca.v3;
        d3 = -d*ca.v2;
        gm = imageValueAt(y1,y2-d2,y3-d3,f);
        gp = imageValueAt(y1,y2+d2,y3+d3,f);
        empl = emp[lmax-ilag] = alignmentError(fm,gp);
        epml = epm[lmax-ilag] = alignmentError(fp,gm);
      } else {
        emp[lmax-ilag] = -empl;
        epm[lmax-ilag] = -epml;
      }
    }

    // Errors for samples below; make any extrapolated errors negative.
    FaultCell cb = cell;
    int nlagb = min(lmax,n1-1-cb.i1);
    y1 = cell.x1; y2 = cell.x2; y3 = cell.x3;
    for (int ilag=1; ilag<=lmax; ++ilag) {
      if (ilag<=nlagb) {
        y[0] = y1; y[1] = y2; y[2] = y3;
        cb = cb.walkDownDipFrom(y);
        y1 = y[0]; y2 = y[1]; y3 = y[2];
        d2 =  d*cb.v3;
        d3 = -d*cb.v2;
        gm = imageValueAt(y1,y2-d2,y3-d3,f);
        gp = imageValueAt(y1,y2+d2,y3+d3,f);
        empl = emp[lmax+ilag] = alignmentError(fm,gp);
        epml = epm[lmax+ilag] = alignmentError(fp,gm);
      } else {
        emp[lmax+ilag] = -empl;
        epm[lmax+ilag] = -epml;
      }
    }
  }

  /**
   * Extrapolates alignment errors emp and epm where not computed. Errors that
   * could not be computed are negative, and are copies of errors for smaller
   * lags that could be computed. (Errors for zero lag can always be
   * computed.) 
   * <p>
   * For each lag with a negative error, this method first attempts to
   * extrapolate using other errors for the same lag stored in cell nabors
   * above or below. This first extrapolation works best when shifts vary
   * slowly with depth.
   * <p> 
   * If this first extrapolation is impossible, because the number of above
   * and below nabors for some lag is too small, then errors are extrapolated
   * using the errors already computed for other lags. Those errors are
   * already stored in the cells, but are negative, so in this second
   * extrapolation we simply change their sign.
   */
  private static void extrapolateErrors(FaultCell[][] cab) {
    int nab = cab.length;

    // For all arrays of cells linked above-below, ...
    for (int iab=0; iab<nab; ++iab) {
      int mab = cab[iab].length;
      float[][] emp = new float[mab][];
      float[][] epm = new float[mab][];

      // Get arrays of errors for all cells in the array.
      for (int jab=0; jab<mab; ++jab) {
        FaultCell c = cab[iab][jab];
        emp[jab] = c.emp;
        epm[jab] = c.epm;
      }
      int lmax = (emp[0].length-1)/2;

      // For each array of errors, ...
      for (int jab=0; jab<mab; ++jab) {

        // For all lags, negative and positive, ...
        for (int ilag=1; ilag<=lmax; ++ilag) {
          int ilagm = lmax-ilag;
          int ilagp = lmax+ilag;

          // Extrapolate for negative lags.
          float empim = emp[jab][ilagm];
          float epmim = epm[jab][ilagm];
          for (int kab=jab; kab<mab && empim<0.0f; ++kab)
            if (emp[kab][ilagm]>=0.0f) // if we find a good emp for this lag,
              empim = emp[kab][ilagm]; // remember it for use below
          for (int kab=jab; kab<mab && epmim<0.0f; ++kab)
            if (epm[kab][ilagm]>=0.0f) // same thing for a good epm
              epmim = epm[kab][ilagm];
          if (empim<0.0f) empim = -empim; // if no good emp, use what we have
          if (epmim<0.0f) epmim = -epmim; // likewise if no good epm
          emp[jab][ilagm] = empim;
          epm[jab][ilagm] = epmim;

          // Extrapolate for positive lags.
          float empip = emp[jab][ilagp];
          float epmip = epm[jab][ilagp];
          for (int kab=jab; kab>=0 && empip<0.0f; --kab)
            if (emp[kab][ilagp]>=0.0f)
              empip = emp[kab][ilagp];
          for (int kab=jab; kab>=0 && epmip<0.0f; --kab)
            if (epm[kab][ilagp]>=0.0f)
              epmip = epm[kab][ilagp];
          if (empip<0.0f) empip = -empip;
          if (epmip<0.0f) epmip = -epmip;
          emp[jab][ilagp] = empip;
          epm[jab][ilagp] = epmip;
        }
      }
    }
  }

  private static void findShiftsMP(
      DynamicWarping dw, FaultCell[][] cab, FaultCell[][] clr) {
    findShifts(dw,true,cab,clr);
  }

  private static void findShiftsPM(
      DynamicWarping dw, FaultCell[][] cab, FaultCell[][] clr) {
    findShifts(dw,false,cab,clr);
  }

  private static void findShifts(
      DynamicWarping dw, boolean mp, FaultCell[][] clr, FaultCell[][] cab) {

    // Arrays of arrays of errors, linked above and below.
    int nab = cab.length;
    float[][][] eab = new float[nab][][];
    for (int iab=0; iab<nab; ++iab) {
      int lab = cab[iab].length;
      eab[iab] = new float[lab][];
      for (int jab=0; jab<lab; ++jab) {
        FaultCell c = cab[iab][jab];
        eab[iab][jab] = mp?c.emp:c.epm;
      }
    }

    // Arrays of arrays of errors, linked left and right.
    int nlr = clr.length;
    float[][][] elr = new float[nlr][][];
    for (int ilr=0; ilr<nlr; ++ilr) {
      int llr = clr[ilr].length;
      elr[ilr] = new float[llr][];
      for (int jlr=0; jlr<llr; ++jlr) {
        FaultCell c = clr[ilr][jlr];
        elr[ilr][jlr] = mp?c.emp:c.epm;
      }
    }

    // Smooth alignment errors in above-below and left-right directions.
    for (int ismooth=0; ismooth<2; ++ismooth) {
      for (float[][] eabi:eab)
        dw.smoothErrors(eabi,eabi);
      for (float[][] elri:elr)
        dw.smoothErrors(elri,elri);
    }

    // Find shifts by accumulating once more and then backtracking.
    for (int iab=0; iab<nab; ++iab) {
      float[][] dab = dw.accumulateForward(eab[iab]);
      float[] s = dw.backtrackReverse(dab,eab[iab]);
      int lab = s.length;
      for (int jab=0; jab<lab; ++jab) {
        FaultCell c = cab[iab][jab];
        if (mp) {
          c.smp += s[jab];
        } else {
          c.spm += s[jab];
        }
      }
    }
  }

  private static void smoothShifts(FaultSkin skin) {
    FaultCell[] cellNabors = new FaultCell[4];
    for (FaultCell cell:skin) {
      float smp = 0.0f;
      float spm = 0.0f;
      float css = 0.0f;
      cellNabors[0] = cell.ca;
      cellNabors[1] = cell.cb;
      cellNabors[2] = cell.cl;
      cellNabors[3] = cell.cr;
      for (FaultCell cellNabor:cellNabors) {
        if (cellNabor!=null) {
          smp += cell.smp+cellNabor.smp;
          spm += cell.spm+cellNabor.spm;
          css += 2.0f;
        }
      }
      cell.smp = smp/css;
      cell.spm = spm/css;
    }
  }

  private static void filterShifts(FaultSkin skin) {
    for (FaultCell cell:skin) {
      if (cell.smp*cell.spm>0.0f) {
        cell.smp = 0.0f;
        cell.spm = 0.0f;
      }
    }
  }

  private static float alignmentError(float f, float g) {
    float fmg = f-g;
    return fmg*fmg;
  }

  private static float imageValueAt(
    float p1, float p2, float p3, float[][][]f) {
    int n1 = f[0][0].length;
    int n2 = f[0].length;
    int n3 = f.length;
    int i1 = max(0,min(n1-1,round(p1)));
    int i2 = max(0,min(n2-1,round(p2)));
    int i3 = max(0,min(n3-1,round(p3)));
    return f[i3][i2][i1];
  }

  private static void trace(String s) {
    System.out.println(s);
  }
}