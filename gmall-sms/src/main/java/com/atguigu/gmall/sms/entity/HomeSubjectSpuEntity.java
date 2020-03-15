package com.atguigu.gmall.sms.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 专题商品
 * 
 * @author lifeline
 * @email 13001196631@163.com
 * @date 2020-03-15 00:35:15
 */
@ApiModel
@Data
@TableName("sms_home_subject_spu")
public class HomeSubjectSpuEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	@ApiModelProperty(name = "id",value = "id")
	private Long id;
	/**
	 * 专题名字
	 */
	@ApiModelProperty(name = "name",value = "专题名字")
	private String name;
	/**
	 * 专题id
	 */
	@ApiModelProperty(name = "subjectId",value = "专题id")
	private Long subjectId;
	/**
	 * spu_id
	 */
	@ApiModelProperty(name = "spuId",value = "spu_id")
	private Long spuId;
	/**
	 * 排序
	 */
	@ApiModelProperty(name = "sort",value = "排序")
	private Integer sort;

}