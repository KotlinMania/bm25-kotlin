// port-lint: source src/lib.rs
package io.github.kotlinmania.bm25

// Upstream `src/lib.rs` is a re-export hub. Top-level items in this Kotlin package are visible
// to consumers without aliasing, so this file holds only the upstream module-level docs and a
// tracking ledger of upstream `pub use` lines. New caller migrations append to the ledger.
//
// pub use embedder::{
//     DefaultTokenizer, Embedder, EmbedderBuilder, Embedding, TokenEmbedder, TokenEmbedding,
// };
// pub use scorer::{ScoredDocument, Scorer};
// pub use search::{Document, SearchEngine, SearchEngineBuilder, SearchResult};
// pub use tokenizer::Tokenizer;
//
// #[cfg(feature = "default_tokenizer")]
// pub use default_tokenizer::{Language, LanguageMode};
//
// Callers migrated:
