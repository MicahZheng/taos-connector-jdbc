package com.taosdata.jdbc.utils;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class StringUtilsTest {

    @Test
    public void isEmptyNull() {
        Assert.assertTrue(StringUtils.isEmpty(null));
    }

    @Test
    public void isEmptyEmpty() {
        Assert.assertTrue(StringUtils.isEmpty(""));
    }

    @Test
    public void isNumericNull() {
        Assert.assertFalse(StringUtils.isNumeric(null));
    }

    @Test
    public void isNumericEmpty() {
        Assert.assertFalse(StringUtils.isNumeric(""));
    }

    @Test
    public void isNumericStr() {
        Assert.assertFalse(StringUtils.isNumeric("abc"));
    }

    @Test
    public void isNumericNeg() {
        Assert.assertFalse(StringUtils.isNumeric("-21"));
    }

    @Test
    public void isNumericPoint() {
        Assert.assertFalse(StringUtils.isNumeric("2.15"));
    }

    @Test
    public void isNumeric() {
        Assert.assertTrue(StringUtils.isNumeric("61"));
    }
}