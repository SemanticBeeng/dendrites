/** 
  * 
  */
package org.gs.filters

import org.scalatest.FlatSpecLike
import org.scalatest.Matchers._
import org.gs.fixtures.TestValuesBuilder

/** @author garystruthers
  *
  */
class ProductFilterSpec extends FlatSpecLike with TestValuesBuilder {

  val mixCaseClasses = keyBigDecimal ++ keyBigInt ++ keyBoolean ++ keyDouble ++ keyFloat ++
    keyInt ++ keyLong ++ keyString ++ keyOptBigDec ++ keyOptBigInt ++ keyOptBool ++ keyOptDouble ++
    keyOptFloat ++ keyOptInt ++ keyOptLong ++ keyOptStr ++ keyEithBigDec ++ keyEithBigInt ++
    keyEithBool ++ keyEithDouble ++ keyEithFloat ++ keyEithInt ++ keyEithLong ++ keyEithStr

  "A Product Filter" should "return all BigDecimal fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isType[BigDecimal])
    val flat = filtered
    val dbug = filtered.find { x => !x.isInstanceOf[BigDecimal] }
    assert(keyBigDecimal.size === filtered.size)
    assert(filtered.forall(isType[BigDecimal]))
  }

  it should "return all BigInt fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isType[BigInt])
    assert(keyBigInt.size === filtered.size)
    assert(filtered.forall(isType[BigInt]))
  }

  it should "return all Boolean fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isType[Boolean])
    assert(keyBoolean.size === filtered.size)
    assert(filtered.forall(isType[Boolean]))
  }

  it should "return all Double fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isType[Double])
    assert(keyDouble.size === filtered.size)
    assert(filtered.forall(isType[Double]))
  }

  it should "return all Float fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isType[Float])
    assert(keyFloat.size === filtered.size)
    assert(filtered.forall(isType[Float]))
  }

  it should "return all Int fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isType[Int])
    assert(keyInt.size === filtered.size)
    assert(filtered.forall(isType[Int]))
  }

  it should "return all Long fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isType[Long])
    assert(keyLong.size === filtered.size)
    assert(filtered.forall(isType[Long]))
  }

  it should "return all String fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isType[String])
    assert(keyString.size * 25 === filtered.size)
    assert(filtered.forall(isType[String]))
  }
/*
  it should "return all Option BigDecimal fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isOptionType[BigDecimal])
    assert(keyOptBigDec.size === filtered.size)
    assert(filtered.forall(isOptionType[BigDecimal]))
  }

  it should "return all Option BigInt fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isOptionType[BigInt])
    assert(keyOptBigInt.size === filtered.size)
    assert(filtered.forall(isOptionType[BigInt]))
  }

  it should "return all Option Boolean fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isOptionType[Boolean])
    assert(keyOptBool.size === filtered.size)
    assert(filtered.forall(isOptionType[Boolean]))
  }

  it should "return all Option Double fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isOptionType[Double])
    assert(keyOptDouble.size === filtered.size)
    assert(filtered.forall(isOptionType[Double]))
  }

  it should "return all Option Float fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isOptionType[Float])
    assert(keyOptFloat.size === filtered.size)
    assert(filtered.forall(isOptionType[Float]))
  }

  it should "return all Option Int fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isOptionType[Int])
    assert(keyOptInt.size === filtered.size)
    assert(filtered.forall(isOptionType[Int]))
  }

  it should "return all Option Long fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isOptionType[Long])
    assert(keyOptLong.size === filtered.size)
    assert(filtered.forall(isOptionType[Long]))
  }

  it should "return all Option String fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isOptionType[String])
    assert(keyOptStr.size === filtered.size)
    assert(filtered.forall(isOptionType[String]))
  }

  it should "return all Either BigDecimal fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isEitherStringRight[BigDecimal])
    assert(keyEithBigDec.size === filtered.size)
    assert(filtered.forall(isEitherStringRight[BigDecimal]))
  }

  it should "return all Either BigInt fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isEitherStringRight[BigInt])
    assert(keyEithBigInt.size === filtered.size)
    assert(filtered.forall(isEitherStringRight[BigInt]))
  }

  it should "return all Either Boolean fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isEitherStringRight[Boolean])
    assert(keyEithBool.size === filtered.size)
    assert(filtered.forall(isEitherStringRight[Boolean]))
  }

  it should "return all Either Double fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isEitherStringRight[Double])
    assert(keyEithDouble.size === filtered.size)
    assert(filtered.forall(isEitherStringRight[Double]))
  }

  it should "return all Either Float fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isEitherStringRight[Float])
    assert(keyEithFloat.size === filtered.size)
    assert(filtered.forall(isEitherStringRight[Float]))
  }

  it should "return all Either Int fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isEitherStringRight[Int])
    assert(keyEithInt.size === filtered.size)
    assert(filtered.forall(isEitherStringRight[Int]))
  }

  it should "return all Either Long fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isEitherStringRight[Long])
    assert(keyEithLong.size === filtered.size)
    assert(filtered.forall(isEitherStringRight[Long]))
  }

  it should "return all Either String fields from a sequence of case classes" in {
    val filtered = filterProducts(mixCaseClasses, productFilter, isEitherStringRight[String])
    assert(keyEithStr.size === filtered.size)
    assert(filtered.forall(isEitherStringRight[String]))
  }*/
}
