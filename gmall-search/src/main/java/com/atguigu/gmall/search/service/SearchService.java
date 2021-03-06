package com.atguigu.gmall.search.service;

import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchParam;
import com.atguigu.gmall.search.pojo.SearchResponseAttrVO;
import com.atguigu.gmall.search.pojo.SearchResponseVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    private static final ObjectMapper objectMapper = new ObjectMapper();


    /**
     * localhost:8086/search?catelog3=225&brand=6&props=33:4000-3000&order=2:asc&priceFrom=100&priceTo=10000&pageNum=1&pageSize=1&keyword=手机
     * @param searchParam
     * @return
     * @throws IOException
     */
    public SearchResponseVO search(SearchParam searchParam) throws IOException {
        // 构建DSL语句
        SearchRequest searchRequest = this.buildQueryDsl(searchParam);
        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(response);
        SearchResponseVO responseVO = this.parseSearchResult(response);
        responseVO.setPageSize(searchParam.getPageSize());
        responseVO.setPageNum(searchParam.getPageNum());
        return responseVO;
    }

    private SearchResponseVO parseSearchResult(SearchResponse response) throws JsonProcessingException {
        SearchResponseVO responseVO = new SearchResponseVO();
        // 1、获取命中的总记录数
        SearchHits hits = response.getHits();
        responseVO.setTotal(hits.totalHits);
        // 2、解析品牌的聚合结果集
        // [{id:100,name:华为,logo:xxx},{id:101,name:小米,log:yyy}]
        SearchResponseAttrVO brand = new SearchResponseAttrVO();
        brand.setName("品牌");
        // 获取品牌的聚合结果集
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();
        ParsedLongTerms brandIdAgg = (ParsedLongTerms) aggregationMap.get("brandIdAgg");
        List<String> brandValues = brandIdAgg.getBuckets().stream().map(bucket -> {
            Map<String, String> map = new HashMap<>();
            // 获取品牌id
            map.put("id", bucket.getKeyAsString());
            // 获取品牌名称：通过子聚合来获取
            Map<String, Aggregation> brandIdSubMap = bucket.getAggregations().asMap();
            ParsedStringTerms brandNameAgg = (ParsedStringTerms) brandIdSubMap.get("brandNameAgg");
            String brandName = brandNameAgg.getBuckets().get(0).getKeyAsString();
            map.put("name", brandName);
            try {
                return objectMapper.writeValueAsString(map);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return null;
        }).collect(Collectors.toList());
        brand.setValue(brandValues);
        responseVO.setBrand(brand);
        // 3、解析分类的聚合结果集
        // [{id:100,name:华为,logo:xxx},{id:101,name:小米,log:yyy}]
        SearchResponseAttrVO category = new SearchResponseAttrVO();
        category.setName("分类");
        // 获取分类的聚合结果集
        ParsedLongTerms categoryIdAgg = (ParsedLongTerms) aggregationMap.get("categoryIdAgg");
        List<String> catValues = categoryIdAgg.getBuckets().stream().map(bucket -> {
            Map<String, String> map = new HashMap<>();
            // 获取品牌id
            map.put("id", bucket.getKeyAsString());
            // 获取品牌名称：通过子聚合来获取
            Map<String, Aggregation> categoryIdSubMap = bucket.getAggregations().asMap();
            ParsedStringTerms categoryNameAgg = (ParsedStringTerms) categoryIdSubMap.get("categoryNameAgg");
            String categoryName = categoryNameAgg.getBuckets().get(0).getKeyAsString();
            map.put("name", categoryName);
            try {
                return objectMapper.writeValueAsString(map);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return null;
        }).collect(Collectors.toList());
        category.setValue(catValues);
        responseVO.setCatelog(category);
        // 4、解析查询列表
        SearchHit[] subHits = hits.getHits();
        List<Goods> goodsList = new ArrayList<>();
        for (SearchHit subHit : subHits) {
            Goods goods = objectMapper.readValue(subHit.getSourceAsString(), new TypeReference<Goods>() {
            });
            goods.setTitle(subHit.getHighlightFields().get("title").getFragments()[0].toString());
            goodsList.add(goods);
        }
        responseVO.setProducts(goodsList);
        // 5、规格参数
        // 获取嵌套聚合对象
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        // 规格参数id聚合对象
        ParsedLongTerms attrIdAgg = (ParsedLongTerms) attrAgg.getAggregations().get("attrIdAgg");
        List<Terms.Bucket> buckets = (List<Terms.Bucket>)attrIdAgg.getBuckets();
        if(!CollectionUtils.isEmpty(buckets)) {
            List<SearchResponseAttrVO> searchResponseAttrVOS = buckets.stream().map(bucket -> {
                SearchResponseAttrVO responseAttrVO = new SearchResponseAttrVO();
                // 设置规格参数的id
                responseAttrVO.setProductAttributeId(bucket.getKeyAsNumber().longValue());
                // 设置规格参数的名称：获取子聚合，从子聚合中获取名称
                List<? extends Terms.Bucket> nameBuckets = ((ParsedStringTerms) bucket.getAggregations().get("attrNameAgg")).getBuckets();
                responseAttrVO.setName(nameBuckets.get(0).getKeyAsString());
                // 设置规格参数值得列表
                List<? extends Terms.Bucket> valueBuckets = ((ParsedStringTerms) bucket.getAggregations().get("attrValueAgg")).getBuckets();
                List<String> values = valueBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                responseAttrVO.setValue(values);
                return responseAttrVO;
            }).collect(Collectors.toList());
            responseVO.setAttrs(searchResponseAttrVOS);
        }
        return responseVO;
    }



    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        // 获取关键字
        String keyword = searchParam.getKeyword();
        if(StringUtils.isEmpty(keyword)) {
            return null;
        }
        // 查询条件构建器
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        // 1、构建查询条件和过滤条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 1.1、构建查询条件
        boolQueryBuilder.must(QueryBuilders.matchQuery("title", keyword).operator(Operator.AND));
        // 1.2、构建过滤条件
        // 1.2.1、构建品牌过滤
        String[] brand = searchParam.getBrand();
        if(brand != null && brand.length != 0) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brand));
        }
        // 1.2.2、 构建分类过滤
        String[] catelog3 = searchParam.getCatelog3();
        if(catelog3 != null && catelog3.length != 0) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId", catelog3));
        }
        // 1.2.3、 构建规格属性嵌套过滤
        String[] props = searchParam.getProps();
        if(props != null && props.length != 0) {
            for (String prop : props) {
                // 以：进行分割，分割后应该是两个元素，1-attrId， 2-attrValue（以 - 分割的字符串）
                String[] split = StringUtils.split(prop, ":");
                // 判断切分之后的字符串是否合法
                if(split == null || split.length != 2) {
                    continue;
                }
                // 以 - 分割处理attrValue
                String[] attrValues = StringUtils.split(split[1], "-");
                // 构建嵌套查询
                BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                // 构建嵌套查询中的子查询
                BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();
                // 构建子查询中的过滤条件
                subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId", split[0]));
                subBoolQuery.must(QueryBuilders.termsQuery("attrs.attrValue", attrValues));
                // 把嵌套查询放入过滤器中
                boolQuery.must(QueryBuilders.nestedQuery("attrs", subBoolQuery, ScoreMode.None));
                boolQueryBuilder.filter(boolQuery);
            }
        }
        // 1.2.4、 价格区间过滤
        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("price");
        Integer priceFrom = searchParam.getPriceFrom();
        Integer priceTo = searchParam.getPriceTo();
        if(priceFrom != null) {
            rangeQueryBuilder.gte(priceFrom);
        }
        if(priceTo != null) {
            rangeQueryBuilder.lte(priceTo);
        }
        boolQueryBuilder.filter(rangeQueryBuilder);
        // 查询条件构建器最后一步
        sourceBuilder.query(boolQueryBuilder);
        // 2、构建分页
        Integer pageNum = searchParam.getPageNum();
        Integer pageSize = searchParam.getPageSize();
        sourceBuilder.from((pageNum - 1) * pageSize);
        sourceBuilder.size(pageSize);
        // 3、构建排序
        String order = searchParam.getOrder();
        if(!StringUtils.isEmpty(order)) {
            String[] split = StringUtils.split(order, ":");
            if(split != null && split.length == 2) {
                String field = null;
                switch (split[0]) {
                    case "1" : field = "sale"; break;
                    case "2" : field = "price"; break;
                }
                sourceBuilder.sort(field, StringUtils.equals("asc", split[1]) ? SortOrder.ASC : SortOrder.DESC);
            }
        }
        // 4、构建高亮
        sourceBuilder.highlighter(new HighlightBuilder().field("title").preTags("<em>").postTags("</em>"));
        // 5、构建聚合
        // 5.1、 品牌聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("brandIdAgg").field("brandId")
                .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName")));
        // 5.2、 分类聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName")));
        // 5.3、 搜索规格属性聚合
        sourceBuilder.aggregation(AggregationBuilders.nested("attrAgg", "attrs")
            .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
            .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
            .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));
        System.out.println(sourceBuilder.toString());
        // 6、结果集过滤
        sourceBuilder.fetchSource(new String[]{"skuId", "pic", "title", "price"}, null);
        // 查询参数
        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        searchRequest.source(sourceBuilder);
        return searchRequest;
    }
    /**
     * GET /goods/_search
     * {
     *   "query": {
     *     "bool": {
     *       "must": [
     *         {
     *           "match": {
     *             "title": {
     *               "query": "手机",
     *               "operator": "and"
     *             }
     *           }
     *         }
     *       ],
     *       "filter": [
     *         {
     *           "terms": {
     *             "brandId": [
     *               "6"
     *             ]
     *           }
     *         },
     *         {
     *           "range": {
     *             "price": {
     *               "gte": 2000,
     *               "lte": 5000
     *             }
     *           }
     *         },
     *         {
     *           "terms": {
     *             "categoryId": [
     *               "225"
     *             ]
     *           }
     *         },
     *         {
     *           "bool": {
     *             "must": [
     *               {
     *                 "nested": {
     *                   "path": "attrs",
     *                   "query": {
     *                     "bool": {
     *                       "must": [
     *                         {
     *                           "term": {
     *                             "attrs.attrName": "电池"
     *                           }
     *                         },
     *                         {
     *                           "terms": {
     *                             "attrs.attrValue": ["4000"]
     *                           }
     *                         }
     *                       ]
     *                     }
     *                   }
     *                 }
     *               }
     *             ]
     *           }
     *         }
     *       ]
     *     }
     *   },
     *   "from": 1,
     *   "size": 1,
     *   "sort": [
     *     {
     *       "price": {
     *         "order": "asc"
     *       }
     *     }
     *   ],
     *   "highlight": {
     *     "fields": {"title": {}},
     *     "pre_tags": "<em>",
     *     "post_tags": "</em>"
     *   },
     *   "aggs": {
     *     "brandIdAgg": {
     *       "terms": {
     *         "field": "brandId"
     *       },
     *       "aggs": {
     *         "brandNameAgg": {
     *           "terms": {
     *             "field": "brandName"
     *           }
     *         }
     *       }
     *     },
     *     "categoryIdAgg": {
     *       "terms": {
     *         "field": "categoryId"
     *       },
     *       "aggs": {
     *         "categoryNameAgg": {
     *           "terms": {
     *             "field": "categoryName"
     *           }
     *         }
     *       }
     *     },
     *     "attrAgg": {
     *       "nested": {
     *         "path": "attrs"
     *       },
     *       "aggs": {
     *         "attrIdAgg": {
     *           "terms": {
     *             "field": "attrs.attrId"
     *           },
     *           "aggs": {
     *             "attrNameAgg": {
     *               "terms": {
     *                 "field": "attrs.attrName"
     *               }
     *             },
     *             "attrValueAgg": {
     *               "terms": {
     *                 "field": "attrs.attrValue"
     *               }
     *             }
     *           }
     *         }
     *       }
     *     }
     *   }
     * }
     */


}
