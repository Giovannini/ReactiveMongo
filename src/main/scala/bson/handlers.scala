package reactivemongo.bson.handlers

import org.jboss.netty.buffer._
import reactivemongo.bson._
import reactivemongo.bson.buffer._
import reactivemongo.core.netty._
import reactivemongo.core.protocol._

/**
 * A typeclass that creates a raw BSON document contained in a [[http://static.netty.io/3.5/api/org/jboss/netty/buffer/ChannelBuffer.html ChannelBuffer]] from a `DocumentType` instance.
 *
 * @tparam DocumentType The type of the instance that can be turned into a BSON document.
 */
trait RawBSONDocumentSerializer[-DocumentType] {
  def serialize(document: DocumentType) :ChannelBuffer
}

/**
 * A typeclass that creates a `DocumentType` instance from a raw BSON document contained in a [[http://static.netty.io/3.5/api/org/jboss/netty/buffer/ChannelBuffer.html ChannelBuffer]].
 *
 * @tparam DocumentType The type of the instance to create.
 */
trait RawBSONDocumentDeserializer[+DocumentType] {
  def deserialize(buffer: ChannelBuffer) :DocumentType
}

/**
 * A typeclass that creates a BSONDocument from a `DocumentType`.
 *
 * @tparam DocumentType The type of the instance that can be turned into a BSONDocument.
 */
trait BSONDocumentSerializer[-DocumentType] extends RawBSONDocumentSerializer[DocumentType] with VariantBSONDocumentWriter[DocumentType] {
  final def serialize(document: DocumentType) = {
    val buffer = ChannelBufferWritableBuffer()
    DefaultBufferHandler.write(buffer, write(document)) // TODO
    buffer.buffer
  }
}

/**
 * A typeclass that creates a `DocumentType from a BSONDocument.
 *
 * @tparam DocumentType The type of the instance to create.
 */
trait BSONDocumentDeserializer[+DocumentType] extends RawBSONDocumentDeserializer[DocumentType] with VariantBSONDocumentReader[DocumentType] {
  final def deserialize(buffer: ChannelBuffer) = {
    val readableBuffer = ChannelBufferReadableBuffer(buffer)
    read(DefaultBufferHandler.readDocument(readableBuffer).get) // TODO
  }
}

/**
 * A handler that produces an Iterator of `DocumentType`,
 * provided that there is an implicit [[reactivemongo.bson.handlers.BSONReader]][DocumentType] in the scope.
 */
trait BSONReaderHandler {
  def handle[DocumentType](reply: Reply, buffer: ChannelBuffer)(implicit reader: RawBSONDocumentDeserializer[DocumentType]) :Iterator[DocumentType]
}

/** Default [[reactivemongo.bson.handlers.BSONReader]], [[reactivemongo.bson.handlers.BSONWriter]], [[reactivemongo.bson.handlers.BSONReaderHandler]]. */
object DefaultBSONHandlers {
  implicit object DefaultBSONReaderHandler extends BSONReaderHandler {
    def handle[DocumentType](reply: Reply, buffer: ChannelBuffer)(implicit reader: RawBSONDocumentDeserializer[DocumentType]) :Iterator[DocumentType] = {
      new Iterator[DocumentType] {
        def hasNext = buffer.readable
        def next = reader.deserialize(buffer.readBytes(buffer.getInt(buffer.readerIndex)))
      }
    }
  }

  implicit object DefaultBSONDocumentReader extends BSONDocumentDeserializer[BSONDocument] {
    def read(doc: BSONDocument) :BSONDocument = doc
  }

  implicit object DefaultBSONDocumentWriter extends BSONDocumentSerializer[BSONDocument] {
    def write(doc: BSONDocument) = doc
  }

  /** Parses the given response and produces an iterator of [[reactivemongo.bson.DefaultBSONIterator]]s. */
  def parse(response: Response) = DefaultBSONReaderHandler.handle(response.reply, response.documents)(DefaultBSONDocumentReader)
}