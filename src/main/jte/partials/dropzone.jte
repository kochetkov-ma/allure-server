@param String name = "file"
@param String accept = ".zip,application/zip,application/x-zip-compressed"
@param boolean required = true
@param String label = "ZIP archive"
@param String hint = "ZIP archive · drop here or click to browse"
<div x-data="{
       isDragging: false,
       fileName: '',
       fileSize: 0,
       handleDrop(e) {
         this.isDragging = false;
         const dt = e.dataTransfer;
         if (!dt || !dt.files.length) return;
         this.$refs.input.files = dt.files;
         this.$refs.input.dispatchEvent(new Event('change', {bubbles:true}));
       },
       handleChange(e) {
         const f = e.target.files && e.target.files[0];
         if (!f) { this.clear(); return; }
         this.fileName = f.name;
         this.fileSize = f.size;
       },
       clear() {
         this.fileName = '';
         this.fileSize = 0;
         this.$refs.input.value = '';
       },
       fmtSize() {
         const s = this.fileSize;
         if (s < 1024) return s + ' B';
         if (s < 1048576) return (s/1024).toFixed(1) + ' KB';
         return (s/1048576).toFixed(2) + ' MB';
       }
     }"
     @dragenter.prevent="isDragging = true"
     @dragover.prevent="isDragging = true"
     @dragleave.prevent="isDragging = false"
     @drop.prevent="handleDrop($event)"
     @click="$refs.input.click()"
     @keydown.enter.prevent="$refs.input.click()"
     @keydown.space.prevent="$refs.input.click()"
     role="button"
     tabindex="0"
     :aria-label="fileName ? ('Selected ' + fileName + ', click to change') : 'Drop file here or click to browse'"
     :class="isDragging ? 'drop-zone--active' : (fileName ? 'drop-zone--loaded' : '')"
     class="drop-zone group">

    <input type="file"
           x-ref="input"
           name="${name}"
           accept="${accept}"
           required="${required}"
           @change="handleChange($event)"
           class="sr-only"
           tabindex="-1">

    <div x-show="!fileName" class="flex flex-col items-center gap-2 pointer-events-none">
        <svg class="h-12 w-12 text-text-muted group-hover:text-primary transition-colors" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 7.5m0 0L7.5 12M12 7.5v9"/>
        </svg>
        <p class="text-sm text-text">
            <span class="font-semibold underline underline-offset-2">Click to browse</span>
            <span class="text-text-muted"> or drag &amp; drop</span>
        </p>
        <p class="text-xs text-text-muted">${hint}</p>
    </div>

    <div x-show="fileName" x-cloak class="flex flex-col items-center gap-1">
        <svg class="h-12 w-12 text-success" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
        </svg>
        <p class="text-sm font-semibold text-text break-all max-w-xl" x-text="fileName"></p>
        <p class="text-xs text-text-muted">
            <span class="sr-only">${label}: </span>
            <span x-text="fmtSize()"></span>
            <span aria-hidden="true"> · </span>
            <button type="button"
                    class="underline hover:text-primary focus:outline-none focus:ring-1 focus:ring-primary rounded"
                    @click.stop="clear()">clear</button>
        </p>
    </div>
</div>
