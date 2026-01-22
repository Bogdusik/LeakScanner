#!/usr/bin/env python3
"""
Generate PNG icons from SVG using cairosvg or svglib
Requires: pip install cairosvg
"""

import os
import sys
import subprocess

SVG_FILE = "icon.svg"
SIZES = [16, 48, 128]

def check_dependencies():
    """Check if required packages are installed"""
    try:
        import cairosvg
        return True, "cairosvg"
    except ImportError:
        try:
            from svglib.svglib import svg2rlg
            from reportlab.graphics import renderPM
            return True, "svglib"
        except ImportError:
            return False, None

def generate_with_cairosvg():
    """Generate icons using cairosvg"""
    import cairosvg
    
    for size in SIZES:
        output_file = f"icon{size}.png"
        cairosvg.svg2png(url=SVG_FILE, write_to=output_file, output_width=size, output_height=size)
        print(f"✅ Generated: {output_file} ({size}x{size})")

def generate_with_svglib():
    """Generate icons using svglib"""
    from svglib.svglib import svg2rlg
    from reportlab.graphics import renderPM
    from PIL import Image
    
    drawing = svg2rlg(SVG_FILE)
    
    for size in SIZES:
        output_file = f"icon{size}.png"
        # Render to PNG
        renderPM.drawToFile(drawing, output_file, fmt='PNG', dpi=72)
        # Resize if needed
        img = Image.open(output_file)
        img = img.resize((size, size), Image.Resampling.LANCZOS)
        img.save(output_file, 'PNG')
        print(f"✅ Generated: {output_file} ({size}x{size})")

def main():
    if not os.path.exists(SVG_FILE):
        print(f"❌ Error: {SVG_FILE} not found")
        sys.exit(1)
    
    has_deps, library = check_dependencies()
    
    if not has_deps:
        print("❌ Error: Required packages not found.")
        print("\n📦 Install one of the following:")
        print("   pip install cairosvg")
        print("   or")
        print("   pip install svglib reportlab pillow")
        print("\n💡 Or use the online converter: https://cloudconvert.com/svg-to-png")
        sys.exit(1)
    
    print(f"🎨 Generating icons using {library}...\n")
    
    try:
        if library == "cairosvg":
            generate_with_cairosvg()
        else:
            generate_with_svglib()
        
        print("\n🎉 All icons generated successfully!")
        print("📁 Files are ready in:", os.getcwd())
        
    except Exception as e:
        print(f"❌ Error generating icons: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
