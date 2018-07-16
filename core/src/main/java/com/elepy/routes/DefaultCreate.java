package com.elepy.routes;

import com.elepy.Elepy;
import com.elepy.concepts.AtomicIntegrityEvaluator;
import com.elepy.concepts.IntegrityEvaluatorImpl;
import com.elepy.concepts.ObjectEvaluator;
import com.elepy.dao.Crud;
import com.elepy.utils.ClassUtils;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import spark.Request;
import spark.Response;

import java.util.List;

public class DefaultCreate<T> implements RouteHandler<T> {


    private void defaultCreate(Response response, T product, Crud<T> dao, ObjectMapper objectMapper, List<ObjectEvaluator<T>> objectEvaluators) throws Exception {
        for (ObjectEvaluator<T> objectEvaluator : objectEvaluators) {
            objectEvaluator.evaluate(product);
        }
        new IntegrityEvaluatorImpl<T>().evaluate(product, dao);
        dao.create(product);
        response.status(200);
        response.body("OK");
    }

    private void multipleCreate(Response response, Iterable<T> items, Crud<T> dao, List<ObjectEvaluator<T>> objectEvaluators) throws Exception {
        if (ClassUtils.hasIntegrityRules(dao.getType())) {
            new AtomicIntegrityEvaluator<T>().evaluate(Lists.newArrayList(Iterables.toArray(items, dao.getType())));
        }

        for (T item : items) {
            for (ObjectEvaluator<T> objectEvaluator : objectEvaluators) {
                objectEvaluator.evaluate(item);
            }
            new IntegrityEvaluatorImpl<T>().evaluate(item, dao);
        }
        dao.create(items);
        response.status(200);
        response.body("OK");

    }

    @Override
    public void handle(Request request, Response response, Crud<T> dao, Elepy elepy, List<ObjectEvaluator<T>> objectEvaluators, Class<T> clazz) throws Exception {
        String body = request.body();

        ObjectMapper objectMapper = elepy.getObjectMapper();
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, dao.getType());

            final List<T> ts = objectMapper.readValue(body, type);
            multipleCreate(response, ts, dao, objectEvaluators);
        } catch (JsonMappingException e) {

            T item = objectMapper.readValue(body, dao.getType());
            defaultCreate(response, item, dao, objectMapper, objectEvaluators);
        }
    }
}
