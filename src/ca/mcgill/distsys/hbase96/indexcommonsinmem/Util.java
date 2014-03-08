package ca.mcgill.distsys.hbase96.indexcommonsinmem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;

import ca.mcgill.distsys.hbase96.indexcommonsinmem.exceptions.InvalidCriterionException;
import ca.mcgill.distsys.hbase96.indexcommonsinmem.exceptions.InvalidQueryException;
import ca.mcgill.distsys.hbase96.indexcommonsinmem.proto.ByteArrayCriterion;
import ca.mcgill.distsys.hbase96.indexcommonsinmem.proto.Column;
import ca.mcgill.distsys.hbase96.indexcommonsinmem.proto.Criterion;
import ca.mcgill.distsys.hbase96.indexcommonsinmem.proto.Criterion.CompareType;
import ca.mcgill.distsys.hbase96.indexcommonsinmem.proto.IndexedColumnQuery;
import ca.mcgill.distsys.hbase96.indexcommonsinmem.proto.Range;
import ca.mcgill.distsys.hbase96.indexcoprocessorsinmem.protobuf.generated.IndexCoprocessorInMem.IndexedQueryRequest;
import ca.mcgill.distsys.hbase96.indexcoprocessorsinmem.protobuf.generated.IndexCoprocessorInMem.ProtoByteArrayCriterion;
import ca.mcgill.distsys.hbase96.indexcoprocessorsinmem.protobuf.generated.IndexCoprocessorInMem.ProtoColumn;
import ca.mcgill.distsys.hbase96.indexcoprocessorsinmem.protobuf.generated.IndexCoprocessorInMem.ProtoCompareType;
import ca.mcgill.distsys.hbase96.indexcoprocessorsinmem.protobuf.generated.IndexCoprocessorInMem.ProtoCriteriaList;
import ca.mcgill.distsys.hbase96.indexcoprocessorsinmem.protobuf.generated.IndexCoprocessorInMem.ProtoRange;
import ca.mcgill.distsys.hbase96.indexcoprocessorsinmem.protobuf.generated.IndexCoprocessorInMem.ProtoCriteriaList.ProtoOperator;
import ca.mcgill.distsys.hbase96.indexcoprocessorsinmem.protobuf.generated.IndexCoprocessorInMem.ProtoKeyValue;
import ca.mcgill.distsys.hbase96.indexcoprocessorsinmem.protobuf.generated.IndexCoprocessorInMem.ProtoResult;

import com.google.protobuf.ByteString;

public class Util {
	
	private static final Log LOG = LogFactory.getLog(Util.class);
	
	// Added by Cong
	// Serialize object to byte array
	public static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ObjectOutputStream o = new ObjectOutputStream(b);
        o.writeObject(obj);
        return b.toByteArray();
    }
	// Added by Cong
	// Deserialize byte array to object
    public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream b = new ByteArrayInputStream(bytes);
        ObjectInputStream o = new ObjectInputStream(b);
        return o.readObject();
    }
	
	
    public static byte[] concatByteArray(byte[] array1, byte[] array2) {
        byte[] result = new byte[array1.length + array2.length];
        int position = 0;

        System.arraycopy(array1, 0, result, position, array1.length);
        position += array1.length;

        System.arraycopy(array2, 0, result, position, array2.length);

        return result;
    }

    public static Result toResult(final ProtoResult protoResult) {
        List<ProtoKeyValue> values = protoResult.getKeyValueList();
        List<KeyValue> keyValues = new ArrayList<KeyValue>(values.size());
        for (ProtoKeyValue kv : values) {
            keyValues.add(toKeyValue(kv));
        }
        return new Result(keyValues);
    }

    public static KeyValue toKeyValue(final ProtoKeyValue kv) {
        return new KeyValue(kv.getRow().toByteArray(), kv.getFamily().toByteArray(), kv.getQualifier().toByteArray(), kv.getTimestamp(),
                KeyValue.Type.codeToType((byte) kv.getKeyType().getNumber()), kv.getValue().toByteArray());
    }

    public static ProtoResult toResult(final Result result) {
        ProtoResult.Builder builder = ProtoResult.newBuilder();
        Cell[] cells = result.raw();
        if (cells != null) {
            for (Cell c : cells) {
                builder.addKeyValue(toKeyValue(c));
            }
        }
        return builder.build();
    }
    
    public static List<ProtoResult> toProtoResults(final List<Result> results) {
        List<ProtoResult> protoResultList = new ArrayList<ProtoResult>(results.size());

        for(Result result: results) {
            protoResultList.add(toResult(result));
        }
        
        return protoResultList;
    }
    
    public static List<Result> toResults(final List<ProtoResult> protoResults) {
        List<Result> resultList = new ArrayList<Result>(protoResults.size());

        for(ProtoResult protoResult: protoResults) {
            resultList.add(toResult(protoResult));
        }
        
        return resultList;
    }

    public static ProtoKeyValue toKeyValue(final Cell kv) {
        ProtoKeyValue.Builder kvbuilder = ProtoKeyValue.newBuilder();
        kvbuilder.setRow(ByteString.copyFrom(kv.getRowArray(), kv.getRowOffset(), kv.getRowLength()));
        kvbuilder.setFamily(ByteString.copyFrom(kv.getFamilyArray(), kv.getFamilyOffset(), kv.getFamilyLength()));
        kvbuilder.setQualifier(ByteString.copyFrom(kv.getQualifierArray(), kv.getQualifierOffset(), kv.getQualifierLength()));
        kvbuilder.setTimestamp(kv.getTimestamp());
        kvbuilder.setValue(ByteString.copyFrom(kv.getValueArray(), kv.getValueOffset(), kv.getValueLength()));
        return kvbuilder.build();
    }

    public static IndexedQueryRequest buildQuery(IndexedColumnQuery query) throws InvalidQueryException {
        IndexedQueryRequest.Builder requestBuilder = IndexedQueryRequest.newBuilder();
        ProtoCriteriaList.Builder criteriaListBuilder = ProtoCriteriaList.newBuilder();

        for (Column queryCol : query.getColumnList()) {
            ProtoColumn.Builder columnBuilder = ProtoColumn.newBuilder();

            if (queryCol.getFamily() == null) {
                throw new InvalidQueryException("Invalid Column in the query, a column MUST have a family.");
            }

            columnBuilder.setFamily(ByteString.copyFrom(queryCol.getFamily()));
            if (queryCol.getQualifier() != null) {
                columnBuilder.setQualifier(ByteString.copyFrom(queryCol.getQualifier()));
            }
            requestBuilder.addColumn(columnBuilder.build());
        }

        if (query.isMustPassAllCriteria()) {
            criteriaListBuilder.setOperator(ProtoOperator.MUST_PASS_ALL);
        } else {
            criteriaListBuilder.setOperator(ProtoOperator.MUST_PASS_ONE);
        }

        marshalCriteria(criteriaListBuilder, query.getCriteria());

        requestBuilder.setCriteriaList(criteriaListBuilder.build());
        return requestBuilder.build();
    }

    private static void marshalCriteria(ProtoCriteriaList.Builder criteriaListBuilder, List<Criterion<?>> queryCriteriaList)
            throws InvalidCriterionException {

        if (queryCriteriaList == null || queryCriteriaList.isEmpty()) {
            throw new InvalidCriterionException("An indexed query must contain at least one comparison criterion to run on an indexed column.");
        }
        for (Criterion<?> queryCriterion : queryCriteriaList) {
            if (queryCriterion.getCompareColumn() == null || queryCriterion.getCompareColumn().getQualifier() == null
                    || queryCriterion.getCompareColumn().getFamily() == null) {
                throw new InvalidCriterionException(
                        "A criterion's must have a non null column comprised of a non null family and non null qualifier.");
            }

            ProtoColumn.Builder columnBuilder = ProtoColumn.newBuilder();
            columnBuilder.setFamily(ByteString.copyFrom(queryCriterion.getCompareColumn().getFamily()));
            columnBuilder.setQualifier(ByteString.copyFrom(queryCriterion.getCompareColumn().getQualifier()));

            if (queryCriterion instanceof ByteArrayCriterion) {
                ProtoByteArrayCriterion.Builder protoByteArrayCriterionBuilder = ProtoByteArrayCriterion.newBuilder();
                protoByteArrayCriterionBuilder.setCompareToValue(ByteString.copyFrom((byte[]) queryCriterion.getComparisonValue()));
                protoByteArrayCriterionBuilder.setCompareColumn(columnBuilder.build());
                ProtoCompareType compareType = getProtoCompareOpFromComparisonType(queryCriterion.getComparisonType());
                protoByteArrayCriterionBuilder.setCompareOp(compareType);
                // Added by Cong
                if(compareType == ProtoCompareType.RANGE) {
                	
                	ProtoRange.Builder rangeBuilder = ProtoRange.newBuilder();
                	Range range = queryCriterion.getRange();
                	rangeBuilder.setLowerBound(ByteString.copyFrom(range.getLowerBound()));
                	rangeBuilder.setHigherBound(ByteString.copyFrom(range.getHigherBound()));
                	protoByteArrayCriterionBuilder.setRange(rangeBuilder.build());
                }
                criteriaListBuilder.addByteArrayCriteria(protoByteArrayCriterionBuilder.build());
            } else {
                throw new InvalidCriterionException("Unknown criterion type: " + queryCriterion.getClass().getName());
            }
        }

    }

    private static ProtoCompareType getProtoCompareOpFromComparisonType(CompareType comparisonType) {
        switch (comparisonType) {
        case EQUAL:
            return ProtoCompareType.EQUAL;
        case GREATER:
            return ProtoCompareType.GREATER;
        case GREATER_OR_EQUAL:
            return ProtoCompareType.GREATER_OR_EQUAL;
        case LESS:
            return ProtoCompareType.LESS;
        case LESS_OR_EQUAL:
            return ProtoCompareType.LESS_OR_EQUAL;
        case NOT_EQUAL:
            return ProtoCompareType.NOT_EQUAL;
        // Added by Cong
        case RANGE:
        	return ProtoCompareType.RANGE;
        default:
            return ProtoCompareType.NO_OP;
        }
    }

    public static IndexedColumnQuery buildQuery(IndexedQueryRequest indexedQueryRequest) {
        IndexedColumnQuery result = new IndexedColumnQuery();

        if (indexedQueryRequest.getCriteriaList().getOperator() == ProtoOperator.MUST_PASS_ALL) {
            result.setMustPassAllCriteria(true);
        } else {
            result.setMustPassAllCriteria(false);
        }

        for (ProtoColumn requestCol : indexedQueryRequest.getColumnList()) {
            Column queryColumn = new Column(requestCol.getFamily().toByteArray());
            if (requestCol.hasQualifier()) {
                queryColumn.setQualifier(requestCol.getQualifier().toByteArray());
            }
            result.addColumn(queryColumn);
        }

        for (ProtoByteArrayCriterion requestBACriterion : indexedQueryRequest.getCriteriaList().getByteArrayCriteriaList()) {
            ByteArrayCriterion queryBACriterion = new ByteArrayCriterion(requestBACriterion.getCompareToValue().toByteArray());

            initCriterionFromRequest(requestBACriterion, queryBACriterion);
            result.addCriterion(queryBACriterion);
        }

        /* TODO: Add processing for other criterion types */

        return result;
    }

    private static void initCriterionFromRequest(Object requestCriterion, Criterion<?> queryCriterion) {
        ProtoCompareType requestCompareType;
        ProtoColumn requestColumn;
        try {
            Method getCompareOp = requestCriterion.getClass().getMethod("getCompareOp");
            requestCompareType = (ProtoCompareType) getCompareOp.invoke(requestCriterion);
            Method getCompareColumn = requestCriterion.getClass().getMethod("getCompareColumn");
            requestColumn = (ProtoColumn) getCompareColumn.invoke(requestCriterion);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        LOG.info("Util.class: " + requestCompareType);
        
        switch (requestCompareType) {
        case EQUAL:
            queryCriterion.setComparisonType(CompareType.EQUAL);
            break;
        case GREATER:
            queryCriterion.setComparisonType(CompareType.GREATER);
            break;
        case GREATER_OR_EQUAL:
            queryCriterion.setComparisonType(CompareType.GREATER_OR_EQUAL);
            break;
        case LESS:
            queryCriterion.setComparisonType(CompareType.LESS);
            break;
        case LESS_OR_EQUAL:
            queryCriterion.setComparisonType(CompareType.LESS_OR_EQUAL);
            break;
        case NOT_EQUAL:
            queryCriterion.setComparisonType(CompareType.NOT_EQUAL);
            break;
        // Added by Cong
        case RANGE:
        	queryCriterion.setComparisonType(CompareType.RANGE);
        	queryCriterion.setRange(((ProtoByteArrayCriterion)requestCriterion).getRange().getLowerBound().toByteArray(), ((ProtoByteArrayCriterion)requestCriterion).getRange().getHigherBound().toByteArray());
        	LOG.info("Util.class: lowerBound" + Bytes.toString(queryCriterion.getRange().getLowerBound()));
        	LOG.info("Util.class: lowerBound" + Bytes.toString(queryCriterion.getRange().getHigherBound()));
        	break;
        default:
            queryCriterion.setComparisonType(CompareType.NO_OP);
            break;
        }

        Column criterionColumn = new Column(requestColumn.getFamily().toByteArray());
        criterionColumn.setQualifier(requestColumn.getQualifier().toByteArray());
        queryCriterion.setCompareColumn(criterionColumn);

    }
}
