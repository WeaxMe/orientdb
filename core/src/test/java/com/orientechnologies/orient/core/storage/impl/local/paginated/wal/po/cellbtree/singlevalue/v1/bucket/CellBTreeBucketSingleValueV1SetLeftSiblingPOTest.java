package com.orientechnologies.orient.core.storage.impl.local.paginated.wal.po.cellbtree.singlevalue.v1.bucket;

import com.orientechnologies.common.directmemory.OByteBufferPool;
import com.orientechnologies.common.directmemory.OPointer;
import com.orientechnologies.orient.core.storage.cache.OCacheEntry;
import com.orientechnologies.orient.core.storage.cache.OCacheEntryImpl;
import com.orientechnologies.orient.core.storage.cache.OCachePointer;
import com.orientechnologies.orient.core.storage.impl.local.paginated.wal.OOperationUnitId;
import com.orientechnologies.orient.core.storage.impl.local.paginated.wal.po.PageOperationRecord;
import com.orientechnologies.orient.core.storage.index.sbtree.singlevalue.v1.CellBTreeBucketSingleValueV1;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.List;

public class CellBTreeBucketSingleValueV1SetLeftSiblingPOTest {
  @Test
  public void testRedo() {
    final int pageSize = 64 * 1024;
    final OByteBufferPool byteBufferPool = new OByteBufferPool(pageSize);
    try {
      final OPointer pointer = byteBufferPool.acquireDirect(false);
      final OCachePointer cachePointer = new OCachePointer(pointer, byteBufferPool, 0, 0);
      final OCacheEntry entry = new OCacheEntryImpl(0, 0, cachePointer);

      CellBTreeBucketSingleValueV1 bucket = new CellBTreeBucketSingleValueV1(entry);
      bucket.init(true);

      bucket.setLeftSibling(24);

      entry.clearPageOperations();

      final OPointer restoredPointer = byteBufferPool.acquireDirect(false);
      final OCachePointer restoredCachePointer = new OCachePointer(restoredPointer, byteBufferPool, 0, 0);
      final OCacheEntry restoredCacheEntry = new OCacheEntryImpl(0, 0, restoredCachePointer);

      final ByteBuffer originalBuffer = cachePointer.getBufferDuplicate();
      final ByteBuffer restoredBuffer = restoredCachePointer.getBufferDuplicate();

      Assert.assertNotNull(originalBuffer);
      Assert.assertNotNull(restoredBuffer);

      restoredBuffer.put(originalBuffer);

      bucket.setLeftSibling(42);

      final List<PageOperationRecord> operations = entry.getPageOperations();
      Assert.assertEquals(1, operations.size());

      Assert.assertTrue(operations.get(0) instanceof CellBTreeBucketSingleValueV1SetLeftSiblingPO);

      final CellBTreeBucketSingleValueV1SetLeftSiblingPO pageOperation = (CellBTreeBucketSingleValueV1SetLeftSiblingPO) operations
          .get(0);

      CellBTreeBucketSingleValueV1<Byte> restoredBucket = new CellBTreeBucketSingleValueV1<>(restoredCacheEntry);

      Assert.assertEquals(24, restoredBucket.getLeftSibling());

      pageOperation.redo(restoredCacheEntry);

      Assert.assertEquals(42, restoredBucket.getLeftSibling());

      byteBufferPool.release(pointer);
      byteBufferPool.release(restoredPointer);
    } finally {
      byteBufferPool.clear();
    }
  }

  @Test
  public void testUndo() {
    final int pageSize = 64 * 1024;

    final OByteBufferPool byteBufferPool = new OByteBufferPool(pageSize);
    try {
      final OPointer pointer = byteBufferPool.acquireDirect(false);
      final OCachePointer cachePointer = new OCachePointer(pointer, byteBufferPool, 0, 0);
      final OCacheEntry entry = new OCacheEntryImpl(0, 0, cachePointer);

      CellBTreeBucketSingleValueV1 bucket = new CellBTreeBucketSingleValueV1(entry);
      bucket.init(true);

      bucket.setLeftSibling(24);

      entry.clearPageOperations();

      bucket.setLeftSibling(42);

      final List<PageOperationRecord> operations = entry.getPageOperations();
      Assert.assertEquals(1, operations.size());

      Assert.assertTrue(operations.get(0) instanceof CellBTreeBucketSingleValueV1SetLeftSiblingPO);

      final CellBTreeBucketSingleValueV1SetLeftSiblingPO pageOperation = (CellBTreeBucketSingleValueV1SetLeftSiblingPO) operations
          .get(0);

      final CellBTreeBucketSingleValueV1<Byte> restoredBucket = new CellBTreeBucketSingleValueV1<>(entry);

      Assert.assertEquals(42, restoredBucket.getLeftSibling());

      pageOperation.undo(entry);

      Assert.assertEquals(24, restoredBucket.getLeftSibling());

      byteBufferPool.release(pointer);
    } finally {
      byteBufferPool.clear();
    }
  }

  @Test
  public void testSerialization() {
    OOperationUnitId operationUnitId = OOperationUnitId.generateId();

    CellBTreeBucketSingleValueV1SetLeftSiblingPO operation = new CellBTreeBucketSingleValueV1SetLeftSiblingPO(42, 24);

    operation.setFileId(42);
    operation.setPageIndex(24);
    operation.setOperationUnitId(operationUnitId);

    final int serializedSize = operation.serializedSize();
    final byte[] stream = new byte[serializedSize + 1];
    int pos = operation.toStream(stream, 1);

    Assert.assertEquals(serializedSize + 1, pos);

    CellBTreeBucketSingleValueV1SetLeftSiblingPO restoredOperation = new CellBTreeBucketSingleValueV1SetLeftSiblingPO();
    restoredOperation.fromStream(stream, 1);

    Assert.assertEquals(42, restoredOperation.getFileId());
    Assert.assertEquals(24, restoredOperation.getPageIndex());
    Assert.assertEquals(operationUnitId, restoredOperation.getOperationUnitId());

    Assert.assertEquals(42, restoredOperation.getPrevLeftSibling());
    Assert.assertEquals(24, restoredOperation.getLeftSibling());
  }
}
