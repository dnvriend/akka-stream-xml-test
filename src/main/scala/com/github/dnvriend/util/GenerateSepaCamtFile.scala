/*
 * Copyright 2015 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.dnvriend.util

import java.io.{ BufferedWriter, FileWriter }
import java.util.UUID

import akka.actor.Terminated
import akka.stream.scaladsl.Source

import scala.concurrent.Future
import scala.io.{ Source ⇒ ScalaIOSource }
import scala.util.{ Failure, Random, Try }
import scala.xml.pull.XMLEventReader

object GenerateSepaCamtFile extends App with CoreServices {

  /**
   * Returns a random UUID as String
   */
  def id = UUID.randomUUID().toString

  /**
   * Returns a random Int between 0 (inclusive) and 50 (exclusive)
   */
  def amount: Int = Random.nextInt(50)

  /**
   * Returns a CAMT header with GroupHeader as String
   */
  def camtHeader: String =
    s"""<?xml version="1.0" encoding="UTF-8"?>
        |<Document xmlns='urn:iso:std:iso:20022:tech:xsd:camt.053.001.02' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>
        |  <BkToCstmrStmt>
        |    <GrpHdr>
        |      <MsgId>$id</MsgId>
        |      <CreDtTm>2013-04-12T10:55:08.66+02:00</CreDtTm>
        |      <MsgPgntn>
        |        <PgNb>1</PgNb>
        |        <LastPgInd>true</LastPgInd>
        |      </MsgPgntn>
        |    </GrpHdr>
        |
    """.stripMargin

  /**
   * Returns the CAMT footer, which contains the BankToCustomerStatement wrapper element and the Document wrapper element.
   */
  def camtFooter: String =
    """ </BkToCstmrStmt>
      |</Document>
    """.stripMargin

  /**
   * A statement contains all the transactions of the account named below, so for the account
   * 'NL20INGB0001234567' it will contain all bank transactions (in this example 45.000 transactions)
   */
  def statementHeader: String =
    s"""<Stmt>
        |            <Id>$id</Id>
        |            <ElctrncSeqNb>1</ElctrncSeqNb>
        |            <CreDtTm>2013-11-04T02:15:00.798+02:00</CreDtTm>
        |            <Acct>
        |                <Id>
        |                    <IBAN>NL20INGB0001234567</IBAN>
        |                </Id>
        |                <Ccy>EUR</Ccy>
        |                <Svcr>
        |                    <FinInstnId>
        |                        <BIC>INGBNL2A</BIC>
        |                    </FinInstnId>
        |                </Svcr>
        |            </Acct>
        |            <Bal>
        |                <Tp>
        |                    <CdOrPrtry>
        |                        <Cd>PRCD</Cd>
        |                    </CdOrPrtry>
        |                </Tp>
        |                <Amt Ccy="EUR">1000.00</Amt>
        |                <CdtDbtInd>CRDT</CdtDbtInd>
        |                <Dt>
        |                    <Dt>2013-11-04</Dt>
        |                </Dt>
        |            </Bal>
  """.stripMargin

  /**
   * Returns the statement footer
   */
  def statementFooter: String = "</Stmt>"

  /**
   * Returns a statement entry, which contains the entry details (which we are interested in)
   */
  def entry: String = {
    val amt: Int = amount
    s"""<Ntry>
        |                <Amt Ccy="EUR">$amt.0</Amt>
        |                <CdtDbtInd>DBIT</CdtDbtInd>
        |                <Sts>BOOK</Sts>
        |                <BookgDt>
        |                    <Dt>2013-11-04</Dt>
        |                </BookgDt>
        |                <ValDt>
        |                    <Dt>2013-11-04</Dt>
        |                </ValDt>
        |                <AcctSvcrRef>FooBar</AcctSvcrRef>
        |                <BkTxCd>
        |                    <Domn>
        |                        <Cd>PMNT</Cd>
        |                        <Fmly>
        |                            <Cd>IDDT</Cd>
        |                            <SubFmlyCd>UPDD</SubFmlyCd>
        |                        </Fmly>
        |                    </Domn>
        |                    <Prtry>
        |                        <Cd>N245</Cd>
        |                        <Issr>ABNAMRO</Issr>
        |                    </Prtry>
        |                </BkTxCd>
        |                <NtryDtls>
        |                    <TxDtls>
        |                        <Refs>
        |                            <AcctSvcrRef>GB020000597577102AO</AcctSvcrRef>
        |                            <EndToEndId>10954500000014</EndToEndId>
        |                            <MndtId>10LN56ICCT0010213293023101920145</MndtId>
        |                        </Refs>
        |                        <AmtDtls>
        |                            <TxAmt>
        |                                <Amt Ccy="EUR">$amt.0</Amt>
        |                            </TxAmt>
        |                        </AmtDtls>
        |                        <BkTxCd>
        |                            <Domn>
        |                                <Cd>PMNT</Cd>
        |                                <Fmly>
        |                                    <Cd>IDDT</Cd>
        |                                    <SubFmlyCd>UPDD</SubFmlyCd>
        |                                </Fmly>
        |                            </Domn>
        |                            <Prtry>
        |                                <Cd>N245</Cd>
        |                                <Issr>ABNAMRO</Issr>
        |                            </Prtry>
        |                        </BkTxCd>
        |                        <RltdPties>
        |                            <Cdtr>
        |                                <Nm>DORFF</Nm>
        |                            </Cdtr>
        |                            <CdtrAcct>
        |                                <Id>
        |                                    <IBAN>NL65CITC0001122339</IBAN>
        |                                </Id>
        |                            </CdtrAcct>
        |                        </RltdPties>
        |                        <RltdAgts>
        |                            <CdtrAgt>
        |                                <FinInstnId>
        |                                    <BIC>ABNANL2A</BIC>
        |                                </FinInstnId>
        |                            </CdtrAgt>
        |                        </RltdAgts>
        |                        <RmtInf>
        |                            <Ustrd>random</Ustrd>
        |                        </RmtInf>
        |                        <RtrInf>
        |                            <Rsn>
        |                                <Cd>MS03</Cd>
        |                            </Rsn>
        |                        </RtrInf>
        |                    </TxDtls>
        |                </NtryDtls>
        |                <AddtlNtryInf>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quisque et mauris at tellus commodo commodo. Nunc at lacus dolor. Integer consectetur justo in sem ultrices auctor. Vestibulum non rutrum lacus. Donec tempus quam sit amet dolor laoreet, et lacinia sapien egestas. Ut nec iaculis tellus. Quisque lobortis consequat diam eget porta. Ut nec fringilla arcu. Aliquam commodo ultrices auctor. Morbi eros velit, vestibulum nec luctus vel, tristique nec nisi.</AddtlNtryInf>
        |            </Ntry>
    """.stripMargin
  }

  /**
   * Returns the Terminated message when the Actor System has been terminated
   */
  def terminate: Future[Terminated] = {
    Option(system).map { sys ⇒
      sys.terminate()
      sys.whenTerminated
    }.getOrElse {
      Future.failed(new RuntimeException("Could not stop Actor System"))
    }
  }

  /**
   * Returns the created SEPA CAMT file name when the following has happened:
   *
   * <ul>
   *   <li>Created a file in /tmp</li>
   *   <li>Writes 45.000 account statement entries, which is a transaction for a certain account, for one account only</li>
   *   <li>Closes the SEPA CAMT file</li>
   * </ul>
   */
  def writeFile: Future[String] = {
    val fileName: String = s"/tmp/camt-$id"
    val writer = new BufferedWriter(new FileWriter(fileName, true))
    Source(() ⇒ Iterator from 0)
      .take(45001)
      .map {
        case 0     ⇒ camtHeader + statementHeader
        case 45000 ⇒ statementFooter + camtFooter
        case _     ⇒ entry
      }.runForeach(writer.write)
      .flatMap { _ ⇒
        Try(writer.close()) match {
          case Failure(t) ⇒ Future.failed(t)
          case _          ⇒ Future.successful(())
        }
      }
      .flatMap(_ ⇒ Future.successful(fileName))
  }

  /**
   * Returns an XMLEventReader for a certain file.
   */
  def xmlEventReader(name: String) =
    new XMLEventReader(ScalaIOSource.fromFile(name))

  /**
   * Returns the number of XML events in the XML file
   */
  def countEvents(fileName: String): Future[Long] =
    Source(() ⇒ xmlEventReader(fileName)).runFold(0L) { (c, _) ⇒ c + 1 }

  /**
   * The 'Create CAMT File' process. It does the following:
   *
   * <li>
   *   <ul>Create a SEPA CAMT file with 45.000 account statement entries for one account</ul>
   *   <ul>Counts the number of generated XMLEvent(s) that has been processed (which is about 9.3 million)</ul>
   *   <ul>Terminates the Actor system when the process has finished</ul>
   * </li>
   */
  val result: Future[Long] = for {
    fileName ← writeFile
    numOfEvents ← countEvents(fileName)
    _ ← terminate
  } yield numOfEvents

  /**
   * Error handling, just print the stack trace to stderr
   */
  result.recover {
    case t: Throwable ⇒
      t.printStackTrace()
  }

  /**
   * prints to stdout the number of generated events.
   */
  result.foreach(numOfEvents ⇒ println(s"Number of events: $numOfEvents"))
}
