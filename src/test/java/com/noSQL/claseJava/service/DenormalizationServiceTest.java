package com.noSQL.claseJava.service;

import com.noSQL.claseJava.entity.Book;
import com.noSQL.claseJava.entity.Review;
import com.noSQL.claseJava.repository.FavoritesRepository;
import com.noSQL.claseJava.repository.ReviewRepository;
import com.noSQL.claseJava.repository.UserRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas del cálculo del promedio de calificaciones que se guarda en el libro
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DenormalizationServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private FavoritesRepository favoritesRepository;

    @InjectMocks
    private DenormalizationService denormalizationService;

    @Test
    void refreshBookRatingSavesTheRoundedAverageAndTheTotal() {
        // Resultado de la agregación para 3 reseñas: 5, 4 y 4 -> promedio 4.333...
        Document aggregationResult = new Document("_id", "book-1")
                .append("averageRating", 13.0 / 3.0)
                .append("totalReviews", 3);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Review.class), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(aggregationResult), new Document()));

        denormalizationService.refreshBookRating("book-1");

        Document set = savedRatingFields();
        assertThat(set.get("averageRating")).isEqualTo(4.33);
        assertThat(set.get("totalReviews")).isEqualTo(3);
    }

    @Test
    void refreshBookRatingResetsToZeroWhenTheBookHasNoReviews() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Review.class), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(), new Document()));

        denormalizationService.refreshBookRating("book-1");

        Document set = savedRatingFields();
        assertThat(set.get("averageRating")).isEqualTo(0.0);
        assertThat(set.get("totalReviews")).isEqualTo(0);
    }

    // Devuelve los campos que se mandaron a guardar en el libro con $set
    private Document savedRatingFields() {
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(Book.class));
        return (Document) update.getValue().getUpdateObject().get("$set");
    }
}
