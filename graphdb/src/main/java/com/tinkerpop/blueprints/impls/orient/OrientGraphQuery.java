/*
 *
 *  *  Copyright 2010-2016 OrientDB LTD (http://orientdb.com)
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *  * For more information: http://orientdb.com
 *
 */

package com.tinkerpop.blueprints.impls.orient;

import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.orientechnologies.orient.core.tx.OTransaction;
import com.tinkerpop.blueprints.*;
import com.tinkerpop.blueprints.util.DefaultGraphQuery;

import java.util.*;

/**
 * OrientDB implementation for Graph query.
 *
 * @author Luca Garulli (l.garulli--(at)--orientdb.com) (http://orientdb.com)
 */
public class OrientGraphQuery extends DefaultGraphQuery {

  protected static final char   SPACE              = ' ';
  protected static final String OPERATOR_DIFFERENT = "<>";
  protected static final String OPERATOR_NOT       = "not ";
  protected static final String OPERATOR_IS_NOT    = "is not";
  protected static final String OPERATOR_LET       = "<=";
  protected static final char   OPERATOR_LT        = '<';
  protected static final String OPERATOR_GTE       = ">=";
  protected static final char   OPERATOR_GT        = '>';
  protected static final String OPERATOR_EQUALS    = "=";
  protected static final String OPERATOR_IS        = "is";
  protected static final String OPERATOR_IN        = " in ";
  protected static final String OPERATOR_LIKE      = " like ";

  protected static final String QUERY_FILTER_AND  = " and ";
  protected static final String QUERY_FILTER_OR   = " or ";
  protected static final char   QUERY_STRING      = '\'';
  protected static final char   QUERY_SEPARATOR   = ',';
  protected static final char   COLLECTION_BEGIN  = '[';
  protected static final char   COLLECTION_END    = ']';
  protected static final char   PARENTHESIS_BEGIN = '(';
  protected static final char   PARENTHESIS_END   = ')';
  protected static final String QUERY_LABEL_BEGIN = " label in [";
  protected static final String QUERY_LABEL_END   = "]";
  protected static final String QUERY_WHERE       = " where ";
  protected static final String QUERY_SELECT_FROM = "select from ";
  protected static final String SKIP              = " SKIP ";
  protected static final String LIMIT             = " LIMIT ";
  protected static final String ORDERBY           = " ORDER BY ";
  public                 int    skip              = 0;
  public                 String orderBy           = "";
  public                 String orderByDir        = "desc";
  protected String fetchPlan;

  public class OrientGraphQueryIterable<T extends Element> extends DefaultGraphQueryIterable<T> {
    public OrientGraphQueryIterable(final boolean forVertex, final String[] labels) {
      super(forVertex);

      if (labels != null && labels.length > 0)
        // TREAT CLASS AS LABEL

        has("_class", Contains.IN, Arrays.asList(labels));
    }

    protected Set<String> getIndexedKeys(final Class<? extends Element> elementClass) {
      return ((OrientBaseGraph) graph).getIndexedKeys(elementClass, true);
    }

  }

  protected OrientGraphQuery(final Graph iGraph) {
    super(iGraph);
  }

  /**
   * (Blueprints Extension) Sets the labels to filter. Labels are bound to Class names by default.
   *
   * @param labels String vararg of labels
   *
   * @return Current Query Object to allow calls in chain.
   */
  public Query labels(final String... labels) {
    this.labels = labels;
    return this;
  }

  /**
   * Skips first iSkip items from the result set.
   *
   * @param iSkip Number of items to skip on result set
   *
   * @return Current Query Object to allow calls in chain.
   */
  public Query skip(final int iSkip) {
    this.skip = iSkip;
    return this;
  }

  /**
   * (Blueprints Extension) Sets the order of results by a field in ascending (asc) order. This is translated on ORDER BY in the
   * underlying SQL query.
   *
   * @param props Field to order by
   *
   * @return Current Query Object to allow calls in chain.
   */
  public Query order(final String props) {
    this.order(props, orderByDir);
    return this;
  }

  /**
   * (Blueprints Extension) Sets the order of results by a field in ascending (asc) or descending (desc) order based on dir
   * parameter. This is translated on ORDER BY in the underlying SQL query.
   *
   * @param props Field to order by
   * @param dir   Direction. Use "asc" for ascending and "desc" for descending
   *
   * @return Current Query Object to allow calls in chain.
   */
  public Query order(final String props, final String dir) {
    this.orderBy = props;
    this.orderByDir = dir;
    return this;
  }

  /**
   * Returns the result set of the query as iterable vertices.
   */
  @Override
  public Iterable<Vertex> vertices() {
    if (limit == 0)
      return Collections.emptyList();

    OTransaction transaction = ((OrientBaseGraph) graph).getRawGraph().getTransaction();
    if (transaction.isActive() && transaction.getEntryCount() > 0 || hasCustomPredicate()) {
      // INSIDE TRANSACTION QUERY DOESN'T SEE IN MEMORY CHANGES, UNTIL
      // SUPPORTED USED THE BASIC IMPL
      String[] classes = allSubClassesLabels();
      return new OrientGraphQueryIterable<Vertex>(true, classes);
    }
    final StringBuilder text = new StringBuilder(512);

    // GO DIRECTLY AGAINST E CLASS AND SUB-CLASSES
    text.append(QUERY_SELECT_FROM);

    if (((OrientBaseGraph) graph).isUseClassForVertexLabel() && labels != null && labels.length > 0) {
      // FILTER PER CLASS SAVING CHECKING OF LABEL PROPERTY
      if (labels.length == 1)
        // USE THE CLASS NAME
        text.append(OrientBaseGraph.encodeClassName(labels[0]));
      else {
        // MULTIPLE CLASSES NOT SUPPORTED DIRECTLY: CREATE A SUB-QUERY
        String[] classes = allSubClassesLabels();
        return new OrientGraphQueryIterable<Vertex>(true, classes);
      }
    } else
      text.append(OrientVertexType.CLASS_NAME);

    final List<Object> queryParams = manageFilters(text);
    if (!((OrientBaseGraph) graph).isUseClassForVertexLabel())
      manageLabels(queryParams.size() > 0, text);

    if (orderBy.length() > 1) {
      text.append(ORDERBY);
      text.append(orderBy);
      text.append(" ").append(orderByDir).append(" ");
    }
    if (skip > 0 && skip < Integer.MAX_VALUE) {
      text.append(SKIP);
      text.append(skip);
    }

    if (limit > 0 && limit < Integer.MAX_VALUE) {
      text.append(LIMIT);
      text.append(limit);
    }

    final OSQLSynchQuery<OIdentifiable> query = new OSQLSynchQuery<OIdentifiable>(text.toString());

    if (fetchPlan != null)
      query.setFetchPlan(fetchPlan);

    return new OrientElementIterable<Vertex>(((OrientBaseGraph) graph),
        ((OrientBaseGraph) graph).getRawGraph().query(query, queryParams.toArray()));
  }

  private String[] allSubClassesLabels() {

    String[] classes = null;

    if (labels != null && labels.length > 0) {
      List<String> tmpClasses = new ArrayList<String>();
      for (String label : labels) {
        OrientVertexType vertexType = ((OrientBaseGraph) graph).getVertexType(label);
        tmpClasses.add(vertexType.getName());
        Collection<OClass> allSubclasses = vertexType.getAllSubclasses();
        for (OClass klass : allSubclasses) {
          tmpClasses.add(klass.getName());
        }
      }
      classes = tmpClasses.toArray(new String[tmpClasses.size()]);
    }

    return classes;
  }

  /**
   * Returns the result set of the query as iterable edges.
   */
  @Override
  public Iterable<Edge> edges() {
    if (limit == 0)
      return Collections.emptyList();

    if (((OrientBaseGraph) graph).getRawGraph().getTransaction().isActive() || hasCustomPredicate())
      // INSIDE TRANSACTION QUERY DOESN'T SEE IN MEMORY CHANGES, UNTIL
      // SUPPORTED USED THE BASIC IMPL
      return new OrientGraphQueryIterable<Edge>(false, labels);

    if (((OrientBaseGraph) graph).isUseLightweightEdges())
      return new OrientGraphQueryIterable<Edge>(false, labels);

    final StringBuilder text = new StringBuilder(512);

    // GO DIRECTLY AGAINST E CLASS AND SUB-CLASSES
    text.append(QUERY_SELECT_FROM);

    if (((OrientBaseGraph) graph).isUseClassForEdgeLabel() && labels != null && labels.length > 0) {
      // FILTER PER CLASS SAVING CHECKING OF LABEL PROPERTY
      if (labels.length == 1)
        // USE THE CLASS NAME
        text.append(OrientBaseGraph.encodeClassName(labels[0]));
      else {
        // MULTIPLE CLASSES NOT SUPPORTED DIRECTLY: CREATE A SUB-QUERY
        return new OrientGraphQueryIterable<Edge>(false, labels);
      }
    } else
      text.append(OrientEdgeType.CLASS_NAME);

    List<Object> queryParams = manageFilters(text);
    if (!((OrientBaseGraph) graph).isUseClassForEdgeLabel())
      manageLabels(queryParams.size() > 0, text);

    final OSQLSynchQuery<OIdentifiable> query = new OSQLSynchQuery<OIdentifiable>(text.toString());

    if (fetchPlan != null)
      query.setFetchPlan(fetchPlan);

    if (limit > 0 && limit < Integer.MAX_VALUE)
      query.setLimit(limit);

    return new OrientElementIterable<Edge>(((OrientBaseGraph) graph),
        ((OrientBaseGraph) graph).getRawGraph().query(query, queryParams.toArray()));
  }

  /**
   * (Blueprints Extension) Returns the fetch plan used.
   */
  public String getFetchPlan() {
    return fetchPlan;
  }

  /**
   * (Blueprints Extension) Sets the fetch plan to use on returning result set.
   */
  public void setFetchPlan(final String fetchPlan) {
    this.fetchPlan = fetchPlan;
  }

  protected void manageLabels(final boolean usedWhere, final StringBuilder text) {
    if (labels != null && labels.length > 0) {

      if (!usedWhere) {
        // APPEND WHERE
        text.append(QUERY_WHERE);
      } else
        text.append(QUERY_FILTER_AND);

      text.append(QUERY_LABEL_BEGIN);
      for (int i = 0; i < labels.length; ++i) {
        if (i > 0)
          text.append(QUERY_SEPARATOR);
        text.append(QUERY_STRING);
        text.append(labels[i]);
        text.append(QUERY_STRING);
      }
      text.append(QUERY_LABEL_END);
    }
  }

  protected boolean hasCustomPredicate() {
    for (HasContainer has : hasContainers) {
      if (!(has.predicate instanceof Contains) && !(has.predicate instanceof com.tinkerpop.blueprints.Compare))
        return true;
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  protected List<Object> manageFilters(final StringBuilder text) {
    boolean firstPredicate = true;
    List<Object> params = new ArrayList<Object>();
    for (HasContainer has : hasContainers) {
      if (!firstPredicate)
        text.append(QUERY_FILTER_AND);
      else {
        text.append(QUERY_WHERE);
        firstPredicate = false;
      }

      if (has.predicate instanceof Contains) {
        // IN AND NOT_IN
        if (has.predicate == Contains.NOT_IN) {
          text.append(OPERATOR_NOT);
          text.append(PARENTHESIS_BEGIN);
        }
        text.append('`').append(has.key).append('`');

        if (has.value instanceof String) {
          text.append(OPERATOR_LIKE);
          text.append("?");
          params.add(has.value);
//          generateFilterValue(text, has.value);
        } else {
          text.append(OPERATOR_IN);
          text.append(COLLECTION_BEGIN);

          boolean firstItem = true;
          for (Object o : (Collection<Object>) has.value) {
            if (!firstItem)
              text.append(QUERY_SEPARATOR);
            else
              firstItem = false;
            text.append("?");
            params.add(o);
//            generateFilterValue(text, o);
          }

          text.append(COLLECTION_END);
        }

        if (has.predicate == Contains.NOT_IN)
          text.append(PARENTHESIS_END);
      } else {
        // ANY OTHER OPERATORS
        text.append('`').append(has.key).append('`');
        text.append(SPACE);

        if (has.predicate instanceof com.tinkerpop.blueprints.Compare) {
          final com.tinkerpop.blueprints.Compare compare = (com.tinkerpop.blueprints.Compare) has.predicate;
          boolean appendParam = true;
          switch (compare) {
          case EQUAL:
            if (has.value == null) {
              // IS
              text.append(OPERATOR_IS);
              text.append(" NULL ");
              appendParam = false;
            } else
              // EQUALS
              text.append(OPERATOR_EQUALS);
            break;
          case GREATER_THAN:
            text.append(OPERATOR_GT);
            break;
          case GREATER_THAN_EQUAL:
            text.append(OPERATOR_GTE);
            break;
          case LESS_THAN:
            text.append(OPERATOR_LT);
            break;
          case LESS_THAN_EQUAL:
            text.append(OPERATOR_LET);
            break;
          case NOT_EQUAL:
            if (has.value == null) {
              text.append(OPERATOR_IS_NOT);
              text.append(" NULL ");
              appendParam = false;
            } else
              text.append(OPERATOR_DIFFERENT);
            break;
          }
          text.append(SPACE);
          if (appendParam) {
            text.append("?");
            params.add(has.value);
          }
//          generateFilterValue(text, has.value);
        }

        if (has.value instanceof Collection<?>)
          text.append(PARENTHESIS_END);
      }
    }
    return params;
  }

  protected void generateFilterValue(final StringBuilder text, final Object iValue) {
    if (iValue instanceof String)
      text.append(QUERY_STRING);

    final Object value;

    if (iValue instanceof Date)
      value = ((Date) iValue).getTime();
    else if (iValue != null)
      value = iValue.toString().replace("'", "\\'");
    else
      value = null;

    text.append(value);

    if (iValue instanceof String)
      text.append(QUERY_STRING);
  }
}
