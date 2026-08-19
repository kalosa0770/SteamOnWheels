import os
import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

app = FastAPI(title="Steam on Wheels Bemba Translation API")

MODEL_ID = "Wana1708/nllb-bemba-education"
SRC_LANG = "eng_Latn"
TGT_LANG = "bem_Latn"

print("Loading tokenizer and model from Hugging Face...")
tokenizer = AutoTokenizer.from_pretrained(MODEL_ID, src_lang=SRC_LANG, tgt_lang=TGT_LANG)
model = AutoModelForSeq2SeqLM.from_pretrained(MODEL_ID, low_cpu_mem_usage=True)
model.eval()
print("Model loaded successfully!")

class TranslationRequest(BaseModel):
    inputs: str
    src_lang: str = SRC_LANG
    tgt_lang: str = TGT_LANG

@app.get("/")
def health():
    return {"status": "online", "model": MODEL_ID}

@app.post("/translate")
def translate(req: TranslationRequest):
    if not req.inputs or not req.inputs.strip():
        raise HTTPException(status_code=400, detail="Input text cannot be empty")
    
    inputs = tokenizer(req.inputs, return_tensors="pt")
    forced_bos_token_id = tokenizer.convert_tokens_to_ids(req.tgt_lang)

    with torch.inference_mode():
        generated_tokens = model.generate(
            **inputs,
            forced_bos_token_id=forced_bos_token_id,
            max_length=128
        )
    result = tokenizer.batch_decode(generated_tokens, skip_special_tokens=True)[0]
    return [{"translation_text": result}]