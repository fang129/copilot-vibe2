# 项目测试规范
- 所有单元测试使用 JUnit 5 (Jupiter) + AssertJ + Mockito
- 测试类命名：{原类名}Test，放在src/test/java对应包下
- 测试方法命名：test{方法名}_{场景}_{预期结果}
- 必须添加@DisplayName标注测试场景
- controller层使用@WebMvcTest + MockMvc
- service层使用@ExtendWith(MockitoExtension.class) + Mockito
- util类必须覆盖空值、边界、异常场景
- 每个测试方法只测一个逻辑，独立可运行